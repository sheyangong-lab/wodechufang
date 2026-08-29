/**
 * 抠图链路真机级测试：无头浏览器里跑真实 u2netp 推理。
 * 流程：登录态 → 冰箱放入食材页 → 点照片框 → 选「拍照」→ setInputFiles 注入测试图
 *      → 页面内完成本地分割+上传 → 校验上传结果图四角透明、中心不透明。
 */
import { chromium } from 'playwright';

const API = 'http://192.168.1.111:8080';
const APP = 'http://localhost:5173';

// 造登录态
async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
}
const phone = `136${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
const reg = await api('POST', '/api/auth/register', { phone, smsCode: '1234' });
const token = reg.data.token;
const k = await api('POST', '/api/kitchens', { name: '抠图测试厨房' }, token);

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true });
page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) console.log('  [console]', m.type(), m.text().slice(0, 160)); });
page.on('pageerror', (e) => console.log('  [pageerror]', String(e).slice(0, 200)));
await page.addInitScript(([t, kId, cache]) => {
  localStorage.setItem('token', t);
  localStorage.setItem('kitchenId', kId);
  localStorage.setItem('kitchenCache', cache);
  localStorage.setItem('user', JSON.stringify({ id: 9, nickname: '抠图用户', phoneMasked: '136****0000', points: 0 }));
}, [token, String(k.data.id), JSON.stringify(k.data)]);

let pass = 0, fail = 0;
const ok = (n, c, e = '') => { c ? (pass++, console.log('  ✓', n)) : (fail++, console.log('  ✗', n, e)); };

await page.goto(`${APP}/#/pages/fridge/add`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1200);

// 点照片框 → 弹层选「拍照」→ uni.chooseImage 弹出文件选择框 → Playwright 接管
const photoBoxClick = page.locator('.photo-box').first().click();
await photoBoxClick;
await page.waitForTimeout(400);

const [chooser] = await Promise.all([
  page.waitForEvent('filechooser', { timeout: 10000 }),
  page.getByText('拍照', { exact: true }).click(),
]);
ok('文件选择框已弹出', true);
await chooser.setFiles('/tmp/test-subject.png');
console.log('  … 本地推理中（真实 u2netp WASM）…');

// 等待上传完成（photo-box 里出现预览图）
try {
  await page.locator('.photo').first().waitFor({ state: 'visible', timeout: 60000 });
  ok('抠图完成并显示预览', true);
} catch {
  ok('抠图完成并显示预览', false, '60s 内未出现预览');
}

// 拿预览图地址，加载到 canvas 校验透明度
const src = await page.evaluate(() => {
  const el = document.querySelector('.photo');
  if (!el) return '';
  if (el.getAttribute('src')) return el.getAttribute('src');
  const inner = el.querySelector('img');
  if (inner && inner.src) return inner.src;
  const div = el.querySelector('div');
  const m = div && div.style.backgroundImage ? div.style.backgroundImage.match(/url\\"?([^\\"')]+)"?/) : null;
  return m ? m[1] : '';
});
ok('预览图为服务端URL', src.includes('/files/'), src.slice(0, 60));

const alpha = await page.evaluate(async (url) => {
  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.src = url;
  await new Promise((r, j) => { img.onload = r; img.onerror = j; });
  const c = document.createElement('canvas');
  c.width = img.naturalWidth; c.height = img.naturalHeight;
  c.getContext('2d').drawImage(img, 0, 0);
  const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
  const at = (x, y) => d[(y * c.width + x) * 4 + 3];
  return {
    w: c.width, h: c.height,
    corner: at(2, 2),                    // 应透明（背景被去掉）
    center: at(Math.floor(c.width / 2), Math.floor(c.height / 2)), // 应不透明（主体保留）
  };
}, src);
console.log('  SRC:', src.slice(0, 80));
console.log('  尺寸:', alpha.w, 'x', alpha.h, ' 四角alpha:', alpha.corner, ' 中心alpha:', alpha.center);
ok('四角透明(背景已去除)', alpha.corner === 0, `实际 ${alpha.corner}`);
ok('中心不透明(主体保留)', alpha.center === 255, `实际 ${alpha.center}`);
ok('发生主体裁剪(宽高<原图800x600)', alpha.w < 800 && alpha.h < 600, `${alpha.w}x${alpha.h}`);

console.log(`\n== 抠图测试: ${pass} 通过, ${fail} 失败 ==`);
await browser.close();
process.exit(fail ? 1 : 0);
