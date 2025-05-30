package jp.arkw.alps.fe;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallback;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Context context;
    private File fileId;
    private File fileLog;
    private String BACKEND_URL;

    private SunmiPrinterService sunmiPrinterService;
    public static int NoSunmiPrinter = 0x00000000;
    public static int CheckSunmiPrinter = 0x00000001;
    public static int FoundSunmiPrinter = 0x00000002;
    public static int LostSunmiPrinter = 0x00000003;
    public int sunmiPrinter = CheckSunmiPrinter;

    private final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private final int REQUEST_PERMISSION = 1000;

    private ArrayList<Item> items = new ArrayList<>();
    private ArrayList<Map<String, String>> listSelect = new ArrayList<>();
    private SimpleAdapter simpleAdapter;
    private TextView textView;
    private TextView textViewIP;
    private int total = 0;
    private int id = 0;
    private JSONObject display = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initSunmiPrinterService(this);

        try {
            InputStream inputStream = this.getAssets().open("BACKEND_URL.env");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            BACKEND_URL = bufferedReader.readLine();
            inputStream.close();
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        context = getApplicationContext();
        fileId = new File(context.getFilesDir(), "id.txt");
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileId))) {
            String text = bufferedReader.readLine();
            id = Integer.parseInt(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileLog = new File(context.getFilesDir(), "log.txt");

        items.add(new Item("ﾄﾚｲﾝｼﾐｭﾚｰﾀｸｯｸﾌﾞｯｸ", 500, R.drawable.book, R.drawable.qr1));
        items.add(new Item("ｴﾝｼﾞﾆｱの中国語入門 第2版", 300, R.drawable.book, R.drawable.qr2));

        textView = findViewById(R.id.text_view);
        textViewIP = findViewById(R.id.text_view_ip);
        ListView listViewSelect = findViewById(R.id.list_select);
        simpleAdapter = new SimpleAdapter(
            this,
            listSelect,
            android.R.layout.simple_list_item_2,
            new String[] {"name", "detail"},
            new int[] {android.R.id.text1, android.R.id.text2}
        );
        listViewSelect.setAdapter(simpleAdapter);

        ArrayList<Map<String, Object>> listItem = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", items.get(i).getName());
            item.put("detail", "￥ " + items.get(i).getPrice());
            item.put("image", items.get(i).getImage());
            listItem.add(item);
        }
        ListView listViewItem = findViewById(R.id.list_item);
        listViewItem.setAdapter(new SimpleAdapter(
            this,
            listItem,
            R.layout.list_item,
            new String[] {"name", "detail", "image"},
            new int[] {R.id.name, R.id.detail, R.id.image}
        ));
        listViewItem.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
                items.get(index).setQuantity(items.get(index).getQuantity() + 1);
                update();
                setCustomerDisplay(items.get(index).getName(), "" + items.get(index).getPrice(), "小計", "" + total);
            }
        });

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties linkProperties = connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork());
        List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
        for (int i = 0; i < linkAddresses.size(); i++) {
            String address = linkAddresses.get(i).getAddress().toString().replace("/", "");
            if (address.contains("192")) {
                textViewIP.setText("IP: " + address);
                break;
            }
        }
        setCustomerDisplay("", "", "", "");
        try {
            WebServer webServer = new WebServer();
            webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        findViewById(R.id.button_purchase).setOnClickListener(this);
        findViewById(R.id.button_card).setOnClickListener(this);
        findViewById(R.id.button_clear).setOnClickListener(this);
        checkPermission();
        update();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_purchase) {
            setCustomerDisplay("", "", "合計", "" + total);
            Intent intent = new Intent(getApplication(), PurchaseActivity.class);
            intent.putExtra("total", total);
            startActivityForResult(intent, 1);
        } else if (v.getId() == R.id.button_card) {
            printImage(BitmapFactory.decodeResource(getResources(), R.drawable.card));
            feedPaper(5);
        } else if (v.getId() == R.id.button_clear) {
            clearQuantity();
            setCustomerDisplay("", "", "", "");
        }
    }

    private void clearQuantity() {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setQuantity(0);
        }
        total = 0;
        update();
    }

    private void update() {
        listSelect.clear();
        total = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.getQuantity() >= 1) {
                Map<String, String> map = new HashMap<>();
                int subtotal = item.getPrice() * item.getQuantity();
                map.put("name", item.getName());
                map.put("detail", "￥ " + item.getPrice() + " × " + item.getQuantity() + " = ￥ " + subtotal);
                listSelect.add(map);
                total += subtotal;
            }
        }
        simpleAdapter.notifyDataSetChanged();
        textView.setText("合計: ￥ " + total);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            id++;
            if (id >= 10000) {
                id = 0;
            }
            try (FileWriter writer = new FileWriter(fileId, false)) {
                writer.write("" + id);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String payment = intent.getStringExtra("PAYMENT");
            int cash = intent.getIntExtra("CASH", 0);
            int change = intent.getIntExtra("CHANGE", 0);
            boolean isMoney = intent.getBooleanExtra("IS_MONEY", false);
            // レシート印刷
            printImage(BitmapFactory.decodeResource(getResources(), R.drawable.receipt1));
            printText("技術書典18\nオフライン出展 (リアル会場) こ06\n", 0);
            printImage(BitmapFactory.decodeResource(getResources(), R.drawable.receipt2));
            printText("ご購入になりました商品の\n", 0);
            printText("サポート情報はこちら ↓\n", 0);
            printText("https://arkw.work/doujin\n", 0);
            Date date = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd(E) HH:mm");
            printText("レジ0001 " + simpleDateFormat.format(date) + "\n", 0);
            printText("取" + String.format("%04d", id) + " 責: 01 荒川\n\n", 0);
            int total = 0;
            for (int i = 0; i < items.size(); i++) {
                final Item item = items.get(i);
                if (item.getQuantity() >= 1) {
                    printText("" + item.getName() + " × " + item.getQuantity() + "\n", 0);
                    final int subtotal = item.getPrice() * item.getQuantity();
                    total += subtotal;
                    printText("" + subtotal + "\n", 2);
                }
            }
            printLine();
            printDoubleText("合計", "￥ " + total);
            if (isMoney == true) {
                setCustomerDisplay("お預かり", "" + cash, "お釣り", "" + change);
                printDoubleText("お預かり", "￥ " + cash);
            } else {
                setCustomerDisplay(payment, "" + cash, "お釣り", "" + change);
                printDoubleText(payment, "￥ " + cash);
            }
            printDoubleText("お釣り", "￥ " + change);
            printLine();
            printText("X/Twitter: @arkw0\n", 0);
            printText("Misskey: @arkw@mi.arkw.work\n", 0);
            printText("Website: https://arkw.net/\n", 0);
            printText("E-mail: mail@arkw.net\n", 0);
            // ダウンロードカードの印刷
            for (int i = 0; i < items.size(); i++) {
                final Item item = items.get(i);
                if (item.getQuantity() >= 1) {
                    if (payment.contains(getResources().getString(R.string.payment_tbf)) == false) {
                        printText("\n------------ ｷ ﾘ ﾄ ﾘ ------------\n", 1);
                        printImage(BitmapFactory.decodeResource(getResources(), item.getPrint()));
                        printText("[無断転載・公開禁止]\nダウンロード期限: 2025年6月30日\n読み取れない時はﾚｼｰﾄに記載の\n連絡先へお問い合わせ下さい\n", 1);
                    }
                }
            }
            feedPaper(4);
            FileWriter filewriter = null;
            // 端末に購買記録をロギング
            try {
                filewriter = new FileWriter(fileLog, true);
                for (int i = 0; i < items.size(); i++) {
                    final Item item = items.get(i);
                    if (item.getQuantity() >= 1) {
                        filewriter.write("[ITEM] timestamp=" + simpleDateFormat.format(date) + ", id=" + id + ", name=" + item.getName() + ", price=" + item.getPrice() + ", quantity=" + item.getQuantity() + "\r\n");
                    }
                }
                filewriter.write("[PURCHASE] timestamp=" + simpleDateFormat.format(date) + ", id=" + id + ", total=" + total + ", payment=" + payment + ", cash=" + cash + ", change=" + change + "\r\n");
                filewriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // バックエンドに購買記録を送信
            JSONArray jsonArray = new JSONArray();
            for (int i = 0; i < items.size(); i++) {
                JSONObject jsonObject = new JSONObject();
                final Item item = items.get(i);
                if (item.getQuantity() >= 1) {
                    try {
                        jsonObject.put("name", item.getName());
                        jsonObject.put("price", "" + item.getPrice());
                        jsonObject.put("quantity", "" + item.getQuantity());
                        jsonArray.put(jsonObject);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            JSONObject json = new JSONObject();
            try {
                json.put("id", "" + id);
                json.put("items", jsonArray);
                json.put("total", "" + total);
                json.put("payment", "" + payment);
                json.put("cash", "" + cash);
                json.put("change", "" + change);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            sendPost("/record", json);
            clearQuantity();
        }
    }

    private void printText(String text, int alignment) {
        try {
            sunmiPrinterService.setAlignment(alignment, new InnerResultCallback() {
                @Override
                public void onRunResult(boolean isSuccess) throws RemoteException {
                }
                @Override
                public void onReturnString(String result) throws RemoteException {
                }
                @Override
                public void onRaiseException(int code, String msg) throws RemoteException {
                }
                @Override
                public void onPrintResult(int code, String msg) throws RemoteException {
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        };
        try {
            sunmiPrinterService.printText(text, new InnerResultCallback() {
                @Override
                public void onRunResult(boolean isSuccess) throws RemoteException {
                }
                @Override
                public void onReturnString(String result) throws RemoteException {
                }
                @Override
                public void onRaiseException(int code, String msg) throws RemoteException {
                }
                @Override
                public void onPrintResult(int code, String msg) throws RemoteException {
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        };
    }

    private void printDoubleText(String textLeft, String textRight) {
        try {
            sunmiPrinterService.printColumnsText(new String[]{textLeft, textRight}, new int[]{23, 8}, new int[]{0, 2}, new InnerResultCallback() {
                @Override
                public void onRunResult(boolean isSuccess) throws RemoteException {
                }
                @Override
                public void onReturnString(String result) throws RemoteException {
                }
                @Override
                public void onRaiseException(int code, String msg) throws RemoteException {
                }
                @Override
                public void onPrintResult(int code, String msg) throws RemoteException {
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        };
    }

    private void printImage(Bitmap bitmap) {
        try {
            sunmiPrinterService.printBitmap(bitmap, new InnerResultCallback() {
                @Override
                public void onRunResult(boolean isSuccess) throws RemoteException {
                }
                @Override
                public void onReturnString(String result) throws RemoteException {
                }
                @Override
                public void onRaiseException(int code, String msg) throws RemoteException {
                }
                @Override
                public void onPrintResult(int code, String msg) throws RemoteException {
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        };
    }

    private void printLine() {
        printText("--------------------------------\n", 1);
    }

    private void feedPaper(int n) {
        try {
            sunmiPrinterService.lineWrap(n, new InnerResultCallback() {
                @Override
                public void onRunResult(boolean isSuccess) throws RemoteException {
                }
                @Override
                public void onReturnString(String result) throws RemoteException {
                }
                @Override
                public void onRaiseException(int code, String msg) throws RemoteException {
                }
                @Override
                public void onPrintResult(int code, String msg) throws RemoteException {
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        };
    }

    private InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            checkSunmiPrinterService(service);
        }
        @Override
        protected void onDisconnected() {
            sunmiPrinterService = null;
            sunmiPrinter = LostSunmiPrinter;
        }
    };

    private void checkSunmiPrinterService(SunmiPrinterService service) {
        boolean ret = false;
        try {
            ret = InnerPrinterManager.getInstance().hasPrinter(service);
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
        sunmiPrinter = ret?FoundSunmiPrinter:NoSunmiPrinter;
    }

    public void initSunmiPrinterService(Context context) {
        try {
            boolean ret =  InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback);
            if (!ret) {
                sunmiPrinter = NoSunmiPrinter;
            }
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
    }

    private void setCustomerDisplay(String upperLeft, String upperRight, String lowerLeft, String lowerRight) {
        try {
            display.put("upper_left", upperLeft);
            display.put("upper_right", upperRight);
            display.put("lower_left", lowerLeft);
            display.put("lower_right", lowerRight);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendPost(String url, JSONObject json) {
        new SendPostAsyncTask() {
            @Override
            protected void onPostExecute(String response) {
                try {
                    JSONObject respData = new JSONObject(response);
                } catch (JSONException e) {
                }
            }
        }.execute( new SendPostTaskParams(
                BACKEND_URL + url,
                json.toString()
        ));
    }

    private void checkPermission(){
        if (isGranted() == false){
            requestPermissions(PERMISSIONS, REQUEST_PERMISSION);
        }
    }

    private boolean isGranted(){
        for (int i = 0; i < PERMISSIONS.length; i++){
            // 初回はPERMISSION_DENIEDが返る
            if (checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED) {
                // 一度リクエストが拒絶された場合にtrueを返す．初回または「今後表示しない」が選択された場合falseを返す．
                if (shouldShowRequestPermissionRationale(PERMISSIONS[i])) {
                    Toast.makeText(this, "本アプリの実行には許可が必要です", Toast.LENGTH_LONG).show();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION){
            checkPermission();
        }
    }

    private class WebServer extends NanoHTTPD {
        public WebServer() {
            super(5300);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String msg;
            if (session.getUri().equals("/display")) {
                msg = display.toString();
            } else {
                msg = "{}";
            }
            Response response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", msg);
            return response;
        }
    }
}
