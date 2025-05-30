<template>
  <div>
    <canvas class="canvas" ref="canvas" width="1920" height="1200" @click="click"></canvas>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      status: false,
      image: new Image(),
      imageWidth: 1920,
      imageHeight: 1200,
      canvas: null,
      context: null,
      isFullScreen: false
    }
  },
  mounted() {
    this.canvas = this.$refs.canvas
    this.context = this.canvas.getContext('2d')
    if (import.meta.env.MODE == 'development') {
      this.image.src = '/assets/background.png'
    } else {
      this.image.src = './assets/background.png'
    }

    this.update()
  },
  methods: {
    async update() {
      let url
      if (import.meta.env.MODE == 'development') {
        url = '/api'
      } else {
        url = import.meta.env.VITE_BACKEND_URL
      }
      const res = await this.axios.get(url + '/display', {
        headers: {
          'Cache-Control': 'no-cache',
          'Content-Type': 'application/x-www-form-urlencoded',
          'Access-Control-Allow-Origin': '*'
        }
      })
      let data = res.data
      await this.context.drawImage(this.image, 0, 0, this.imageWidth, this.imageHeight)
      if (
        data.upper_left != '' ||
        data.upper_right != '' ||
        data.lower_left != '' ||
        data.lower_right != ''
      ) {
        this.context.fillStyle = '#fff'
        await this.context.fillRect(
          0,
          this.imageHeight / 2 + 180,
          this.imageWidth,
          this.imageHeight / 2 + 300
        )
        this.context.font = '160px KosugiMaruRegular'
        this.context.fillStyle = '#000'
        this.context.textAlign = 'left'
        await this.context.fillText(data.upper_left, 30, this.imageHeight / 2 + 358)
        await this.context.fillText(data.lower_left, 30, this.imageHeight / 2 + 550)
        this.context.fillStyle = '#fff'
        await this.context.fillRect(
          this.imageWidth / 2 + 330,
          this.imageHeight / 2 + 180,
          this.imageWidth / 2 - 180,
          this.imageHeight / 2 + 300
        )
        this.context.fillStyle = '#000'
        this.context.textAlign = 'right'
        await this.context.fillText(
          data.upper_right,
          this.imageWidth - 30,
          this.imageHeight / 2 + 358
        )
        await this.context.fillText(
          data.lower_right,
          this.imageWidth - 30,
          this.imageHeight / 2 + 550
        )
      }
      const vm = this
      setTimeout(() => {
        vm.update()
      }, 1000 / 2)
    },
    click() {
      if (this.isFullScreen == false) {
        document.body.requestFullscreen()
        this.isFullScreen = true
      } else {
        document.exitFullscreen()
        this.isFullScreen = false
      }
    }
  }
}
</script>
