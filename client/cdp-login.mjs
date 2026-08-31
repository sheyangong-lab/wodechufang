// 通过 CDP(原生WebSocket) 在手机 WebView 里执行 JS：登录→注入存储→跳厨房页
const WS_URL = 'ws://127.0.0.1:9223/devtools/page/5F4EFC981ED2121DF1A5574779E4D1DB'
  || 'ws://localhost:9222/devtools/page/DF22005595C19601D8E65F829BF51F8F';
const API = 'http://192.168.1.187:8080';

const ws = new WebSocket(WS_URL);
let id = 0;
const pending = new Map();

ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
};

function send(method, params) {
  return new Promise((resolve) => {
    id += 1;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(expression) {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true });
  if (r.result?.exceptionDetails) {
    return 'EXC: ' + JSON.stringify(r.result.exceptionDetails.exception ?? {}).slice(0, 300);
  }
  return r.result?.result?.value;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

ws.onopen = async () => {
  console.log('CDP open');
  // 1. 手机侧经 uni.request（原生 CapacitorHttp）登录
  await evaluate(`
    window.__login = 'pending';
    uni.request({
      url: '${API}/api/auth/login',
      method: 'POST',
      data: { phone: '13800002222', smsCode: '1234' },
      success: (r) => { window.__login = JSON.stringify(r.data); },
      fail: (e) => { window.__login = 'FAIL:' + JSON.stringify(e); },
    });
  `);
  await sleep(4000);
  const login = await evaluate('window.__login');
  console.log('LOGIN:', String(login).slice(0, 200));
  if (!login || login === 'pending' || login.startsWith('FAIL')) process.exit(1);

  const data = JSON.parse(login).data;
  const token = data.token;

  await evaluate(`
    uni.setStorageSync('token', ${JSON.stringify(token)});
    uni.setStorageSync('user', JSON.stringify({ id: 1, nickname: '店长', phoneMasked: '138****2222', points: 0 }));
    'stored'
  `);

  // 2. 厨房列表 → 注入 → 跳转
  await evaluate(`
    window.__mine = 'pending';
    uni.request({
      url: '${API}/api/kitchens/mine',
      header: { Authorization: 'Bearer ' + uni.getStorageSync('token') },
      success: (r) => { window.__mine = JSON.stringify(r.data); },
      fail: (e) => { window.__mine = 'FAIL:' + JSON.stringify(e); },
    });
  `);
  await sleep(3000);
  const mine = await evaluate('window.__mine');
  console.log('MINE:', String(mine).slice(0, 160));
  const k = JSON.parse(mine).data.at(-1);
  await evaluate(`
    uni.setStorageSync('kitchenId', '${k.id}');
    uni.setStorageSync('kitchenCache', ${JSON.stringify(JSON.stringify(k))});
    uni.reLaunch({ url: '/pages/kitchen/kitchen' });
    'relaunched'
  `);
  console.log('OK: kitchen', k.id, k.name);
  process.exit(0);
};
