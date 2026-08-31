/**
 * 浏览器自测：验证本轮 9 项改动的主链路。
 * 用法: node e2e-selftest.mjs   (需先启动后端 :8080 与 dev:h5 :5173)
 */
import { chromium } from 'playwright';

const API = 'http://localhost:8080';
const APP = 'http://localhost:5173';
const SHOT_DIR = '/tmp/sk-shots';
import { mkdirSync } from 'fs';
mkdirSync(SHOT_DIR, { recursive: true });

let pass = 0, fail = 0;
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; console.log(`  ✗ ${name} ${extra}`); }
}

async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
}

const phone = `137${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
const reg = await api('POST', '/api/auth/register', { phone, smsCode: '1234' });
const token = reg.data.token;
const k = await api('POST', '/api/kitchens', { name: '自测厨房' }, token);
const kid = k.data.id;
await api('POST', `/api/kitchens/${kid}/categories`, { name: '荤菜' }, token);
await api('POST', `/api/kitchens/${kid}/dishes`, { name: '番茄炒蛋', priceFen: 1280 }, token);
await api('POST', `/api/kitchens/${kid}/dishes`, { name: '红烧肉', priceFen: 2880 }, token);
const cats = await api('GET', `/api/kitchens/${kid}/ledger/categories`, null, token);
const catByName = Object.fromEntries(cats.data.map((c) => [c.name, c.name]));
await api('POST', `/api/kitchens/${kid}/ledger/entries`,
  { type: 'EXPENSE', category: catByName['食材采购'], amountFen: 5000, remark: '买菜' }, token);
await api('POST', `/api/kitchens/${kid}/ledger/entries`,
  { type: 'EXPENSE', category: catByName['水电燃气'], amountFen: 3000, remark: '燃气' }, token);
await api('POST', `/api/kitchens/${kid}/ledger/entries`,
  { type: 'INCOME', category: catByName['菜品销售'], amountFen: 12800, remark: '订单' }, token);
// 完成一单，让菜品销售排行有数据
const dishList = await api('GET', `/api/kitchens/${kid}/dishes?mode=order`, null, token);
const order = await api('POST', `/api/kitchens/${kid}/orders`,
  { items: [{ dishId: dishList.data[0].id, quantity: 2 }] }, token);
await api('POST', `/api/orders/${order.data.id}/complete`, null, token);

const kitchenCache = JSON.stringify(k.data);
const user = JSON.stringify({ id: 1, nickname: '自测用户', phoneMasked: phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2'), points: 66 });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true });
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e)));

await page.addInitScript(([t, kId, cache, u]) => {
  localStorage.setItem('token', t);
  localStorage.setItem('kitchenId', kId);
  localStorage.setItem('kitchenCache', cache);
  localStorage.setItem('user', u);
}, [token, String(kid), kitchenCache, user]);

const goto = (path) => page.goto(`${APP}/#${path}`, { waitUntil: 'networkidle' });
const sleep = (ms) => page.waitForTimeout(ms);

// 1. 我的页（浅色）：宫格图标 + 无会员横幅 + 外观模式入口
console.log('== 我的页(浅色) ==');
await goto('/pages/profile/profile');
await sleep(800);
const bodyText = await page.evaluate(() => document.body.innerText);
ok('无会员横幅', !bodyText.includes('会员尊享'));
ok('有外观模式入口', bodyText.includes('外观模式'));
ok('宫格含厨房管理', bodyText.includes('厨房管理'));
ok('已删任务大厅/我的积分/新手教程/提点意见/平台客服',
   !bodyText.includes('任务大厅') && !bodyText.includes('我的积分') && !bodyText.includes('新手教程')
   && !bodyText.includes('提点意见') && !bodyText.includes('平台客服'));
ok('积分显示已移除', !bodyText.includes('积分'));
const iconCount = await page.locator('img[src*="grid-"], img[src*="chefhat"]').count();
ok('宫格图标已加载(≥5)', iconCount >= 5, `实际${iconCount}`);
await page.screenshot({ path: `${SHOT_DIR}/01-profile-light.png`, fullPage: true });

// 2. 切深色
console.log('== 外观切深色 ==');
await page.getByText('外观模式').click();
await sleep(400);
await page.getByText('深色', { exact: true }).click();
await sleep(600);
const bodyBg = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--sk-bg').trim());
ok('页面已切深色(全局CSS变量)', bodyBg.toLowerCase() === '#16130f', `实际 ${bodyBg}`);
await page.screenshot({ path: `${SHOT_DIR}/02-profile-dark.png`, fullPage: true });

// 3. 账本总统计（深色）：饼图 + 曲线 canvas 有内容
console.log('== 账本总统计(深色) ==');
await goto('/pages/ledger/ledger');
await sleep(800);
await page.getByText('总统计', { exact: true }).click();
await sleep(900);
const pieBg = await page.evaluate(() => {
  const el = document.querySelector('.pie');
  return el ? getComputedStyle(el).backgroundImage || getComputedStyle(el).backgroundColor : '';
});
ok('饼图conic-gradient已渲染', pieBg.includes('conic-gradient'), `实际 ${pieBg.slice(0, 60)}`);
const canvasInk = await page.evaluate(() => {
  const host = document.getElementById('trend-canvas');
  const cv = host && host.querySelector('canvas');
  if (!cv) return -1;
  const ctx = cv.getContext('2d');
  const d = ctx.getImageData(0, 0, cv.width, cv.height).data;
  let n = 0;
  for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++;
  return n;
});
ok('消费曲线canvas已绘制', canvasInk > 500, `非透明像素 ${canvasInk}`);
const ledgerText = await page.evaluate(() => document.body.innerText);
ok('菜品销售排行已随账本独立而移除', !ledgerText.includes('菜品销售排行'));
await page.screenshot({ path: `${SHOT_DIR}/03-ledger-stats-dark.png`, fullPage: true });

// 4. 账本浅色截图 + 记一笔弹层
await page.evaluate(() => localStorage.setItem('themeMode', 'light'));
await page.reload();
await sleep(900);
await page.getByText('总统计', { exact: true }).click();
await sleep(800);
await page.screenshot({ path: `${SHOT_DIR}/04-ledger-stats-light.png`, fullPage: true });
await page.getByText('记一笔', { exact: false }).first().click();
await sleep(500);
await page.getByText('分类', { exact: true }).click();
await sleep(400);
const sheetText = await page.evaluate(() => document.body.innerText);
ok('记一笔分类走自研弹层(含食材采购)', sheetText.includes('食材采购'));
await page.screenshot({ path: `${SHOT_DIR}/05-ledger-add-sheet.png` });

// 5. 随机点菜 E2E（本轮修复的 500 bug）
console.log('== 随机点菜 ==');
await goto('/pages/order/random');
await sleep(700);
await page.getByText('⇄ 随机点菜').click();
await sleep(900);
const randomText = await page.evaluate(() => document.body.innerText);
ok('随机点菜返回菜品(无服务器开小差)', randomText.includes('番茄炒蛋') || randomText.includes('红烧肉'));
ok('无报错弹层', !randomText.includes('开小差'));
await page.screenshot({ path: `${SHOT_DIR}/06-random.png`, fullPage: true });

// 6. 放入食材页：照片框 + 名称布局
console.log('== 放入食材 ==');
await goto('/pages/fridge/add');
await sleep(700);
const addText = await page.evaluate(() => document.body.innerText);
ok('照片入口存在(拍照/选图/自动去背景)', addText.includes('自动去背景'));
await page.screenshot({ path: `${SHOT_DIR}/07-fridge-add.png`, fullPage: true });

// 7. 分类管理页（新增）
await goto('/pages/ledger/categories');
await sleep(700);
const catPage = await page.evaluate(() => document.body.innerText);
ok('账本分类管理页(支出/收入分组)', catPage.includes('支出分类') && catPage.includes('收入分类') && catPage.includes('食材采购'));
await page.screenshot({ path: `${SHOT_DIR}/08-ledger-categories.png`, fullPage: true });

const realErrors = pageErrors.filter((e) => !e.includes('ResizeObserver'));
ok('无未捕获JS异常', realErrors.length === 0, realErrors.join(' | ').slice(0, 200));

console.log(`\n== 自测结果: ${pass} 通过, ${fail} 失败 ==`);
console.log(`截图目录: ${SHOT_DIR}`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
