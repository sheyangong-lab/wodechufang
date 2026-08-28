/**
 * 全局主题常量 —— 来源：docs/复刻方案与需求设计.md 第二节「浅黄暖色」色板。
 * 所有页面/组件取色必须引用此处，禁止散落硬编码。
 */
export const theme = {
  /** 页面背景：奶油白 */
  bg: '#FFFBF2',
  /** 卡片背景 */
  card: '#FFFFFF',
  /** 主色（品牌黄）：Tab 选中、链接、描边按钮 */
  primary: '#F5C15C',
  /** 主按钮（琥珀）：底色+白字；按压态 pressed */
  primaryBtn: '#EFA63C',
  primaryBtnPressed: '#E0A93C',
  /** 主色浅底：选中 chip、公告栏、高亮卡片 */
  primaryLight: '#FDF3DC',
  /** 头部渐变 */
  headerGradient: 'linear-gradient(180deg, #FFE9B8 0%, #FFF8E8 100%)',
  /** 文字 */
  title: '#3D3325',
  sub: '#8C7F6A',
  /** 分割线 */
  divider: '#F0E7D8',
  /** 状态色 */
  success: '#7BC47F',
  warning: '#E8A23D',
  danger: '#E05B4E',
  /** 会员金渐变 */
  vipGradient: 'linear-gradient(90deg, #E8C87E 0%, #D9A441 100%)',
  /** 账本金额 */
  income: '#E8842E',
  expense: '#5BA47C',
} as const;
