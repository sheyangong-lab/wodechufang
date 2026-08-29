<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi, uploadImage, yuanToFen, fenToYuan, fullUrl } from '@/api/dish';
import { chooseSubjectImage } from '@/utils/image-pick';
import type { CategoryView, DishSpec, DishView } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import InputDialog from '@/components/input-dialog.vue';

const kitchenId = ref<number | null>(null);
const dishId = ref<number | null>(null); // 有值=编辑
const saving = ref(false);
const showAdvanced = ref(false);
const editorCtx = ref<any>(null);
const editorReady = ref(false);
const stepImageCount = ref(0);
const originalSteps = ref('');
const editorTouched = ref(false);
const suppressInput = ref(false); // 程序化灌入内容时屏蔽 input 事件

const imageUrl = ref('');
const name = ref('');
const description = ref('');
const categories = ref<CategoryView[]>([]);
const categoryId = ref<number | null>(null);
const stars = ref(0);
const priceYuan = ref('');
const multiSpec = ref(false);
const specs = ref<DishSpec[]>([{ name: '', priceFen: 0 }]);
const shareSquare = ref(false);
const materials = ref('');
const steps = ref('');
const servings = ref('');
const cookMinutes = ref('');
const difficulty = ref('简单');
const calories = ref('');

const difficulties = ['简单', '有点难度', '压力略大'];
const starTexts = ['', '尝鲜', '家常', '拿手', '招牌', '镇店'];
const priceHint = computed(() => (multiSpec.value ? '多规格已开启，按规格定价' : '价格会显示在点单页'));

onLoad((query) => {
  kitchenId.value = getCurrentKitchenId();
  if (!kitchenId.value) {
    uni.showToast({ title: '请先进入厨房', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 800);
    return;
  }
  loadCategories();
  if (query && query.id) {
    dishId.value = Number(query.id);
    uni.setNavigationBarTitle({ title: '编辑菜谱' });
    fillFromServer(dishId.value);
  }
});

async function fillFromServer(id: number) {
  try {
    const d: DishView = await dishApi.detail(id);
    imageUrl.value = d.imageUrl || '';
    name.value = d.name;
    description.value = d.description;
    categoryId.value = d.categoryId;
    stars.value = d.recommendStars;
    priceYuan.value = fenToYuan(d.priceFen);
    const specsLoaded = d.specsJson ? (JSON.parse(d.specsJson) as DishSpec[]) : [];
    if (specsLoaded.length > 0) {
      multiSpec.value = true;
      specs.value = specsLoaded.map((s) => ({ ...s, priceFen: s.priceFen }));
    }
    shareSquare.value = d.shareSquare === 1;
    materials.value = d.materials;
    originalSteps.value = d.steps;
    // 富文本：编辑器就绪后异步灌入历史内容
    waitEditor(() => setEditorHtml(d.steps));
    servings.value = d.servings;
    cookMinutes.value = d.cookMinutes ? String(d.cookMinutes) : '';
    difficulty.value = d.difficulty || '简单';
    calories.value = d.calories;
  } catch {
    // toast 已统一弹出
  }
}

/** 旧数据是纯文本换行；含标签则剥离为纯文本行 */
function toPlainLines(steps: string): string[] {
  if (!steps) return [];
  if (steps.includes('<')) {
    return steps
      .replace(/<img[^>]*>/g, '\n[图]\n')
      .replace(/<\/(p|div|h\d|li)>/g, '\n')
      .replace(/<br\s*\/?>/g, '\n')
      .replace(/<[^>]+>/g, '')
      .split('\n')
      .map((l) => l.trim())
      .filter((l) => l && l !== '[图]');
  }
  return steps.split('\n').filter((l) => l.trim());
}

function setEditorHtml(html: string) {
  const ctx = editorCtx.value;
  if (!ctx) return;
  const lines = toPlainLines(html);
  if (lines.length === 0) return;
  suppressInput.value = true;
  // 首选 delta（跨端一致性最好），失败回退逐行 insertText
  ctx.setContents({
    delta: { ops: lines.map((l) => ({ insert: l + '\n' })) },
    success: () => (suppressInput.value = false),
    fail: () => {
      let chain: Promise<void> = Promise.resolve();
      lines.forEach((l) => {
        chain = chain.then(() => ctx.insertText({ text: l + '\n' }));
      });
      chain.then(() => (suppressInput.value = false));
    },
  });
}

function onEditorReady() {
  // 跨端获取 EditorContext 的官方方式（H5/App/小程序通用）
  uni.createSelectorQuery()
    .select('#steps-editor')
    .context((res: any) => {
      if (res && res.context) {
        editorCtx.value = res.context;
        editorReady.value = true;
      }
    })
    .exec();
}

function waitEditor(cb: () => void) {
  if (editorReady.value) return cb();
  const timer = setInterval(() => {
    if (editorReady.value) {
      clearInterval(timer);
      cb();
    }
  }, 200);
  setTimeout(() => clearInterval(timer), 8000);
}

function syncSteps(e: any) {
  if (suppressInput.value) return; // 程序灌入内容触发的 input 不算用户编辑
  steps.value = e.detail.html;
  stepImageCount.value = (e.detail.html.match(/<img/g) || []).length;
  editorTouched.value = true;
}

function fmt(cmd: string, value?: string) {
  editorCtx.value?.format(cmd, value);
}

function insertStepImage() {
  if (stepImageCount.value >= 8) {
    uni.showToast({ title: '步骤最多 8 张图', icon: 'none' });
    return;
  }
  uploadImage().then((url) => {
    editorCtx.value?.insertImage({ src: fullUrl(url), width: '80%' });
  }).catch(() => {});
}

function clearSteps() {
  editorCtx.value?.clear();
  steps.value = '';
  stepImageCount.value = 0;
  editorTouched.value = true;
}

async function loadCategories() {
  if (!kitchenId.value) return;
  try {
    categories.value = await dishApi.categories(kitchenId.value);
  } catch {
    // ignore
  }
}

// 头图：拍照/相册 → 本地识别主体去背景 → 失败自动回退原图
const imgSourceVisible = ref(false);

function chooseImage() {
  imgSourceVisible.value = true;
}

const imgSourceItems = [
  { key: 'camera', title: '拍照', desc: '拍完自动识别主体、去除背景' },
  { key: 'album', title: '从相册选择', desc: '选完自动识别主体、去除背景' },
];

async function onImgSourcePick(key: string) {
  imgSourceVisible.value = false;
  try {
    const { url, segmented } = await chooseSubjectImage([key as 'camera' | 'album']);
    imageUrl.value = url;
    uni.showToast({ title: segmented ? '已识别主体并去背景' : '已使用原图', icon: 'none' });
  } catch {
    // 取消或上传失败（失败已有 toast）
  }
}

function pickCategory() {
  if (categories.value.length === 0) {
    catDialogVisible.value = true;
    return;
  }
  catSheetVisible.value = true;
}

const catSheetVisible = ref(false);
const catItems = computed(() =>
  categories.value.map((c) => ({ key: String(c.id), title: c.name, desc: `${c.dishCount}道菜` }))
);

function onCatPick(key: string) {
  catSheetVisible.value = false;
  categoryId.value = Number(key);
}

const catDialogVisible = ref(false);

function onCatCreate(name: string) {
  catDialogVisible.value = false;
  dishApi.createCategory(kitchenId.value!, name).then((c) => {
    loadCategories();
    categoryId.value = c.id;
  });
}

const diffSheetVisible = ref(false);

function pickDifficulty() {
  diffSheetVisible.value = true;
}

const diffItems = difficulties.map((d) => ({ key: d, title: d }));

function onDiffPick(key: string) {
  diffSheetVisible.value = false;
  difficulty.value = key;
}

function toggleSpecs(e: any) {
  multiSpec.value = e.detail.value;
  if (multiSpec.value && specs.value.length === 0) {
    specs.value = [{ name: '', priceFen: 0 }];
  }
}

function addSpec() {
  specs.value.push({ name: '', priceFen: 0 });
}

function removeSpec(i: number) {
  specs.value.splice(i, 1);
}

function onSpecPrice(i: number, e: any) {
  const fen = yuanToFen(e.detail.value);
  specs.value[i].priceFen = fen ?? 0;
}

function submit() {
  if (saving.value || !kitchenId.value) return;
  if (!name.value.trim()) return toast('请输入菜谱名称');
  const fen = yuanToFen(priceYuan.value);
  if (fen == null) return toast('请输入正确的价格');
  if (multiSpec.value) {
    const valid = specs.value.filter((s) => s.name.trim());
    if (valid.length === 0) return toast('至少填写一个规格名称');
  }
  saving.value = true;
  const payload = {
    name: name.value.trim(),
    description: description.value.trim(),
    imageUrl: imageUrl.value || null,
    priceFen: fen,
    specs: multiSpec.value
      ? specs.value.filter((s) => s.name.trim()).map((s) => ({ name: s.name.trim(), priceFen: s.priceFen }))
      : null,
    categoryId: categoryId.value,
    recommendStars: stars.value,
    materials: materials.value,
    // 未触碰编辑器时保留原步骤，防止 setContents 失败导致清空
    steps: editorTouched.value ? steps.value : originalSteps.value,
    servings: servings.value.trim(),
    cookMinutes: cookMinutes.value ? Number(cookMinutes.value) : null,
    difficulty: difficulty.value,
    calories: calories.value.trim(),
    shareSquare: shareSquare.value,
  };
  const req = dishId.value
    ? dishApi.update(dishId.value, payload)
    : dishApi.create(kitchenId.value, payload);
  req
    .then(() => {
      uni.showToast({ title: dishId.value ? '已保存' : '发布成功', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 700);
    })
    .finally(() => (saving.value = false));
}

function toast(title: string) {
  uni.showToast({ title, icon: 'none' });
  throw new Error(title);
}
</script>

<template>
  <view class="page">
    <!-- 头图 -->
    <view class="card section">
      <view class="img-box" hover-class="press-dim" @tap="chooseImage">
        <image v-if="imageUrl" class="img-preview" :src="fullUrl(imageUrl)" mode="aspectFill" />
        <view v-else class="img-holder">
          <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
          <text class="holder-tip">拍照 / 选图（自动去背景）</text>
        </view>
      </view>
      <text class="ai-tip">AI 生图功能 M6 上线（每次消耗 2 币）</text>

      <input v-model="name" class="name-input" maxlength="30" placeholder="设置菜谱名称" placeholder-class="ph" />
      <textarea
        v-model="description"
        class="desc-input"
        :maxlength="200"
        placeholder="输入菜谱描述"
        placeholder-class="ph"
        :auto-height="true"
      />
    </view>

    <!-- 属性区 -->
    <view class="card section">
      <view class="row" hover-class="press-dim" @tap="pickCategory">
        <text class="label">菜谱分类</text>
        <text class="value">{{ categories.find((c) => c.id === categoryId)?.name || '请选择分类' }}</text>
        <text class="chev">›</text>
      </view>
      <view class="row">
        <text class="label">没有合适分类？</text>
        <text class="link" hover-class="press-dim" @tap="catDialogVisible = true">＋ 添加分类</text>
      </view>
      <view class="row col">
        <view class="row-inner">
          <text class="label">推荐</text>
          <view class="stars">
            <view
              v-for="i in 5"
              :key="i"
              class="star"
              :class="{ on: i <= stars }"
              hover-class="press-dim"
              @tap="stars = i"
            >★</view>
            <text class="star-text">{{ starTexts[stars] }}</text>
          </view>
        </view>
      </view>
      <view class="row">
        <text class="label">价格（元）</text>
        <input v-if="!multiSpec" v-model="priceYuan" class="input price" type="digit" placeholder="0.00" placeholder-class="ph" />
        <text v-else class="value">按规格定价</text>
      </view>
      <text class="hint">{{ priceHint }}</text>
      <view class="row">
        <text class="label">是否开启多规格</text>
        <switch :checked="multiSpec" color="#EFA63C" @change="toggleSpecs" />
      </view>
      <view v-if="multiSpec" class="spec-box">
        <view v-for="(s, i) in specs" :key="i" class="spec-row">
          <input v-model="s.name" class="spec-name" placeholder="规格名(小份)" placeholder-class="ph" />
          <input class="spec-price" type="digit" placeholder="价格" placeholder-class="ph"
            :value="s.priceFen ? fenToYuan(s.priceFen) : ''" @input="onSpecPrice(i, $event)" />
          <text class="spec-del" hover-class="press-dim" @tap="removeSpec(i)">删除</text>
        </view>
        <text class="spec-add" hover-class="press-dim" @tap="addSpec">＋ 添加规格</text>
      </view>
    </view>

    <view class="card row single">
      <text class="label">是否分享到广场</text>
      <switch :checked="!!shareSquare" color="#EFA63C" @change="(e: any) => (shareSquare = e.detail.value)" />
    </view>

    <!-- 用料 -->
    <view class="card section">
      <text class="sec-title">用料</text>
      <textarea
        v-model="materials"
        class="area"
        placeholder="例如：鸡蛋:2个&#10;番茄:1个"
        placeholder-class="ph"
        :maxlength="500"
      />
      <text class="hint right">食材和用量之间用「:」隔开，多个用料换行</text>
    </view>

    <!-- 制作过程（富文本编辑器） -->
    <view class="card section">
      <view class="row" style="margin-top:0">
        <text class="sec-title no-margin">制作过程</text>
        <text class="img-count">{{ stepImageCount }}/8 图</text>
      </view>
      <view class="toolbar-rich">
        <text class="tb-btn" hover-class="press-dim" @tap="fmt('bold')">B</text>
        <text class="tb-btn italic" hover-class="press-dim" @tap="fmt('italic')">I</text>
        <text class="tb-btn" hover-class="press-dim" @tap="fmt('underline')">U</text>
        <text class="tb-btn" hover-class="press-dim" @tap="fmt('list', 'ordered')">1.</text>
        <text class="tb-btn" hover-class="press-dim" @tap="fmt('list', 'bullet')">•</text>
        <text class="tb-btn" hover-class="press-dim" @tap="insertStepImage">插图</text>
        <text class="tb-btn del" hover-class="press-dim" @tap="clearSteps">清空</text>
      </view>
      <editor
        id="steps-editor"
        class="editor"
        placeholder="1、热锅冷油…&#10;可加粗、列表、插入步骤图（≤8张）"
        @ready="onEditorReady"
        @input="syncSteps"
      />
    </view>

    <!-- 高级设置 -->
    <view class="card section">
      <view class="row" hover-class="press-dim" @tap="showAdvanced = !showAdvanced">
        <text class="sec-title no-margin">高级设置</text>
        <text class="chev">{{ showAdvanced ? '⌃' : '⌄' }}</text>
      </view>
      <block v-if="showAdvanced">
        <view class="row">
          <text class="label">几人份</text>
          <input v-model="servings" class="input" placeholder="如：1人份,1碗" placeholder-class="ph" />
        </view>
        <view class="row">
          <text class="label">烹饪时长(分钟)</text>
          <input v-model="cookMinutes" class="input" type="number" placeholder="如：15" placeholder-class="ph" />
        </view>
        <view class="row" hover-class="press-dim" @tap="pickDifficulty">
          <text class="label">烹饪难度</text>
          <text class="value">{{ difficulty }}</text>
          <text class="chev">›</text>
        </view>
        <view class="row">
          <text class="label">卡路里</text>
          <input v-model="calories" class="input" placeholder="卡路里/kcal" placeholder-class="ph" />
        </view>
      </block>
    </view>

    <button class="btn-publish" :disabled="saving" hover-class="press-sink" @tap="submit">
      {{ dishId ? '保存修改' : '发布菜谱' }}
    </button>

    <!-- 分类选择 / 难度选择 / 添加分类弹层 -->
    <ActionSheet :visible="catSheetVisible" :items="catItems" @select="onCatPick" @close="catSheetVisible = false" />
    <ActionSheet :visible="imgSourceVisible" :items="imgSourceItems" @select="onImgSourcePick" @close="imgSourceVisible = false" />
    <ActionSheet :visible="diffSheetVisible" :items="diffItems" @select="onDiffPick" @close="diffSheetVisible = false" />
    <InputDialog
      :visible="catDialogVisible"
      title="添加分类"
      placeholder="如：荤菜 / 素菜 / 汤羹"
      :maxlength="10"
      @confirm="onCatCreate"
      @close="catDialogVisible = false"
    />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 80rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  margin-bottom: 20rpx;
}
.section { padding: 28rpx 32rpx; }

.img-box {
  width: 240rpx; height: 240rpx;
  border: 2rpx dashed v-bind('theme.divider');
  border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.img-preview { width: 100%; height: 100%; }
.img-holder { display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.holder-icon { width: 72rpx; height: 72rpx; opacity: 0.6; }
.holder-tip { font-size: 22rpx; color: v-bind('theme.sub'); }
.ai-tip { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 12rpx; }

.name-input {
  margin-top: 24rpx; font-size: 36rpx; font-weight: 600; color: v-bind('theme.title');
  border-bottom: 2rpx solid v-bind('theme.divider'); padding-bottom: 20rpx;
}
.desc-input { margin-top: 20rpx; width: 100%; min-height: 80rpx; font-size: 26rpx; color: v-bind('theme.title'); }
.ph { color: v-bind('theme.sub'); }

.row { display: flex; align-items: center; margin-top: 28rpx; }
.row.first { margin-top: 0; }
.row.col { flex-direction: column; align-items: stretch; }
.row.single { padding: 28rpx 32rpx; justify-content: space-between; }
.row-inner { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.label { font-size: 28rpx; color: v-bind('theme.title'); }
.value { flex: 1; text-align: right; font-size: 26rpx; color: v-bind('theme.sub'); }
.chev { color: v-bind('theme.sub'); font-size: 30rpx; margin-left: 12rpx; }
.link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); }

.stars { display: flex; align-items: center; gap: 8rpx; }
.star { font-size: 40rpx; color: v-bind('theme.divider'); padding: 0 4rpx; }
.star.on { color: v-bind('theme.primaryBtn'); }
.star-text { font-size: 22rpx; color: v-bind('theme.sub'); margin-left: 8rpx; }

.input {
  flex: 1; text-align: right; font-size: 28rpx; color: v-bind('theme.title');
}
.input.price { font-weight: 600; }
.hint { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 12rpx; }
.hint.right { text-align: right; }

.spec-box {
  margin-top: 20rpx; padding: 20rpx;
  background: v-bind('theme.primaryLight'); border-radius: 16rpx;
}
.spec-row { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; }
.spec-name {
  flex: 1; background: #fff; border-radius: 12rpx; padding: 12rpx 20rpx;
  font-size: 26rpx; color: v-bind('theme.title');
}
.spec-price {
  width: 160rpx; background: #fff; border-radius: 12rpx; padding: 12rpx 20rpx;
  font-size: 26rpx; color: v-bind('theme.title'); text-align: right;
}
.spec-del { font-size: 24rpx; color: v-bind('theme.danger'); }
.spec-add { font-size: 26rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }

.sec-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.sec-title.no-margin { margin: 0; }
.img-count { margin-left: auto; font-size: 22rpx; color: v-bind('theme.sub'); }
.toolbar-rich {
  display: flex; gap: 10rpx; align-items: center;
  margin-top: 16rpx; padding: 10rpx;
  background: v-bind('theme.primaryLight'); border-radius: 12rpx;
}
.tb-btn {
  min-width: 64rpx; text-align: center;
  font-size: 26rpx; font-weight: 700; color: v-bind('theme.title');
  background: #fff; border-radius: 8rpx; padding: 8rpx 12rpx;
}
.tb-btn.italic { font-style: italic; }
.tb-btn.del { color: v-bind('theme.danger'); margin-left: auto; font-weight: 400; }
.editor {
  width: 100%; height: 360rpx; margin-top: 16rpx;
  font-size: 26rpx; line-height: 44rpx;
}

.btn-publish {
  margin-top: 12rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 24rpx; font-size: 32rpx; line-height: 96rpx;
}
.btn-publish[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-publish::after { border: none; }
</style>
