/**
 * 全局主题 —— 情侣厨房暖色调（浅黄打底 + 珊瑚粉点缀）。
 * 所有页面/组件取色必须引用此处，禁止散落硬编码。
 *
 * 支持浅色 / 深色 / 跟随系统三种模式：
 * - theme 是 reactive 对象，页面 v-bind('theme.bg') 会随模式切换自动刷新；
 * - 切模式调用 setThemeMode()，内部同步导航栏/TabBar 与全局 CSS 变量。
 */
import { reactive } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'auto';

export interface Palette {
  bg: string;
  card: string;
  /** 卡片描边：给界面加边界感 */
  cardBorder: string;
  primary: string;
  primaryBtn: string;
  primaryBtnPressed: string;
  primaryLight: string;
  headerGradient: string;
  rose: string;
  roseLight: string;
  title: string;
  sub: string;
  divider: string;
  /** 中性底：排行徽标/进度条槽/输入底 */
  chipBg: string;
  success: string;
  successLight: string;
  warning: string;
  warningLight: string;
  danger: string;
  dangerLight: string;
  income: string;
  expense: string;
  /** 导航栏 / TabBar 跟随主题 */
  navBg: string;
  navTitle: string;
  tabBarBg: string;
  /** 图表用 */
  chartTrack: string;
  chartText: string;
}

const lightPalette: Palette = {
  bg: '#FFF8F2',
  card: '#FFFFFF',
  cardBorder: '#F0E7D8',
  primary: '#F5C15C',
  primaryBtn: '#EFA63C',
  primaryBtnPressed: '#E0A93C',
  primaryLight: '#FDF3DC',
  headerGradient: 'linear-gradient(180deg, #FFE3D6 0%, #FFF6EC 100%)',
  rose: '#E8836F',
  roseLight: '#FDEDE6',
  title: '#3D3325',
  sub: '#8C7F6A',
  divider: '#F0E7D8',
  chipBg: '#F6F1E7',
  success: '#7BC47F',
  successLight: '#E9F3EC',
  warning: '#E8A23D',
  warningLight: '#FFFCF2',
  danger: '#E05B4E',
  dangerLight: '#FFF5F3',
  income: '#E8842E',
  expense: '#5BA47C',
  navBg: '#FFFBF2',
  navTitle: '#000000',
  tabBarBg: '#FFFFFF',
  chartTrack: '#F2ECE0',
  chartText: '#8C7F6A',
};

const darkPalette: Palette = {
  bg: '#16130F',
  card: '#221E18',
  cardBorder: '#373026',
  primary: '#F5C15C',
  primaryBtn: '#EFA63C',
  primaryBtnPressed: '#D8922F',
  primaryLight: '#3A2F1C',
  headerGradient: 'linear-gradient(180deg, #33291F 0%, #16130F 100%)',
  rose: '#E8836F',
  roseLight: '#3D2723',
  title: '#F0E9DC',
  sub: '#A79B87',
  divider: '#373026',
  chipBg: '#2C261E',
  success: '#6FBF7B',
  successLight: '#1F3327',
  warning: '#E8A23D',
  warningLight: '#332A16',
  danger: '#E8695C',
  dangerLight: '#3A211E',
  income: '#F0A24C',
  expense: '#6FBF8F',
  navBg: '#16130F',
  navTitle: '#ffffff',
  tabBarBg: '#1D1914',
  chartTrack: '#373026',
  chartText: '#A79B87',
};

/** 全局响应式主题：页面 CSS 里 v-bind('theme.xxx') 引用 */
export const theme = reactive<Palette>({ ...lightPalette });

const MODE_KEY = 'themeMode';
let currentMode: ThemeMode = 'auto';
let mediaQuery: MediaQueryList | null = null;

export function getThemeMode(): ThemeMode {
  return currentMode;
}

function resolvedDark(): boolean {
  if (currentMode === 'auto') {
    // #ifdef H5
    return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
    // #endif
    // #ifndef H5
    return false;
    // #endif
  }
  return currentMode === 'dark';
}

/** 把调色板刷进 theme（reactive，逐字段赋值保持引用稳定） */
function applyPalette(dark: boolean) {
  const p = dark ? darkPalette : lightPalette;
  (Object.keys(p) as (keyof Palette)[]).forEach((k) => {
    theme[k] = p[k];
  });
}

/** H5 端：同步导航栏、TabBar、全局 CSS 变量；APK 端顺带刷原生状态栏 */
function applyChrome(dark: boolean) {
  // #ifdef H5
  try {
    // 页面未就绪时 uni API 会异步拒绝，统一吞掉（onShow 会再刷）
    Promise.resolve(uni.setNavigationBarColor({
      frontColor: dark ? '#ffffff' : '#000000',
      backgroundColor: theme.navBg,
    } as any)).catch(() => {});
    Promise.resolve(uni.setTabBarStyle({
      color: theme.sub,
      selectedColor: theme.primaryBtn,
      backgroundColor: theme.tabBarBg,
    })).catch(() => {});
  } catch { /* 同步抛错也兜住 */ }
  const root = document.documentElement;
  root.style.setProperty('--sk-bg', theme.bg);
  root.style.setProperty('--sk-card-border', theme.cardBorder);
  root.style.setProperty('--sk-press-bg', theme.primaryLight);
  root.style.setProperty('--sk-nav-bg', theme.navBg);
  root.style.setProperty('--sk-nav-title', theme.navTitle);
  root.style.setProperty('--sk-tab-bg', theme.tabBarBg);
  // #endif
  // #ifdef H5
  const cap = (globalThis as Record<string, any>).Capacitor;
  if (cap?.isNativePlatform?.() && cap.getPlatform?.() === 'android') {
    import('@capacitor/status-bar')
      .then(({ StatusBar }) => StatusBar.setBackgroundColor({ color: dark ? '#16130F' : '#FFFBF2' }))
      .catch(() => {});
  }
  // #endif
}

/** 切换主题模式并立即生效（light/dark/auto） */
export function setThemeMode(mode: ThemeMode) {
  currentMode = mode;
  uni.setStorageSync(MODE_KEY, mode);
  const dark = resolvedDark();
  applyPalette(dark);
  applyChrome(dark);
}

/** 启动时调用：读持久化模式 + 监听系统深浅色变化 */
export function initTheme() {
  const saved = uni.getStorageSync(MODE_KEY) as ThemeMode | '';
  currentMode = saved === 'light' || saved === 'dark' || saved === 'auto' ? saved : 'auto';
  setThemeMode(currentMode);
  // #ifdef H5
  if (typeof matchMedia === 'function') {
    mediaQuery = matchMedia('(prefers-color-scheme: dark)');
    const handler = () => { if (currentMode === 'auto') setThemeMode('auto'); };
    if (mediaQuery.addEventListener) mediaQuery.addEventListener('change', handler);
    else mediaQuery.addListener(handler);
  }
  // #endif
}

/** 供「我的」页展示当前生效的是深色还是浅色 */
export function isDarkNow(): boolean {
  return resolvedDark();
}

export const MODE_LABELS: Record<ThemeMode, string> = {
  light: '浅色',
  dark: '深色',
  auto: '跟随系统',
};
