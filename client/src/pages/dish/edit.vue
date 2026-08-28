<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi, uploadImage, yuanToFen, fenToYuan, fullUrl } from '@/api/dish';
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
const priceHint = computed(() => (multiSpec.value ? '多规格已开启，按规格定价' : '可用于开启积分支付'));

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
    steps.value = d.steps;
    servings.value = d.servings;
    cookMinutes.value = d.cookMinutes ? String(d.cookMinutes) : '';
    difficulty.value = d.difficulty || '简单';
    calories.value = d.calories;
  } catch {
    // toast 已统一弹出
  }
}

async function loadCategories() {
  if (!kitchenId.value) return;
  try {
    categories.value = await dishApi.categories(kitchenId.value);
  } catch {
    // ignore
  }
}

function chooseImage() {
  uploadImage()
    .then((url) => (imageUrl.value = url))
    .catch(() => {});
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

function toggleSpecs(e: { detail: { value: boolean } }) {
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

function onSpecPrice(i: number, e: { detail: { value: string } }) {
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
    steps: steps.value,
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
          <text class="holder-tip">点击上传成品图</text>
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

    <!-- 制作过程 -->
    <view class="card section">
      <text class="sec-title">制作过程</text>
      <textarea
        v-model="steps"
        class="area tall"
        placeholder="1、热锅冷油…&#10;2、放入鸡蛋…"
        placeholder-class="ph"
        :maxlength="2000"
      />
      <text class="hint right">步骤配图与富文本 M8 升级，最多 8 张图</text>
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
.star { font-size: 40rpx; color: #e5dfd2; padding: 0 4rpx; }
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
.area {
  width: 100%; min-height: 140rpx; margin-top: 16rpx;
  font-size: 26rpx; color: v-bind('theme.title'); line-height: 44rpx;
}
.area.tall { min-height: 220rpx; }

.btn-publish {
  margin-top: 12rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 24rpx; font-size: 32rpx; line-height: 96rpx;
}
.btn-publish[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-publish::after { border: none; }
</style>
