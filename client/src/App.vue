<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
/* ===== 全局按压反馈（hover-class 用），替代 emoji 时代的"点了没反应" ===== */
/* 变暗：用于文字类可点元素 */
.press-dim {
  opacity: 0.55;
}
/* 浅黄底：用于按钮/卡片行，给出边界感 */
.press-bg {
  background-color: #fdf3dc !important;
}
/* 内陷：用于大按钮 */
.press-sink {
  transform: scale(0.97);
  opacity: 0.85;
}
</style>
