package jp.arkw.alps.fe;

class Item {
    String name;
    int price;
    int image;
    int quantity;
    int print;

    Item(String name, int price, int image, int print) {
        this.name = name;
        this.price = price;
        this.image = image;
        this.quantity = 0;
        this.print = print;
    }

    public String getName() {
        return this.name;
    }

    public int getPrice() {
        return this.price;
    }

    public int getImage() {
        return this.image;
    }

    public int getQuantity() { return this.quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getPrint() { return this.print; }
}
