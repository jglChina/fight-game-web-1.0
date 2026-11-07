/* ==========================================================
 * 游戏主页逻辑
 * ----------------------------------------------------------
 * 职责：
 *   1. 页面守卫 + 读取用户名展示
 *   2. 初始化：GET /api/role/info → 404 显示创建区，200 显示角色卡
 *   3. 属性点分配器：30 点、四项加减、实时换算预览、合计=30 才能创建
 *   4. 创建角色：POST /api/role/create
 *   5. 背包：GET /api/item/list 展示，POST /api/item/use 使用
 *   6. 战斗入口：POST /api/battle/start；有 fg_battleId 时先校验状态
 *   7. 退出登录：清理本地 token 回登录页
 *
 * 依赖（由 common.js 提供）：
 *   FG.guard() / FG.request() / FG.toast() / FG.showLoading() / FG.hideLoading()
 *   FG.getToken() / FG.getUsername() / FG.clearAuth()
 *   FG.getBattleId() / FG.setBattleId()
 * ========================================================== */

/* ---------- 常量 ---------- */
const TOTAL_POINTS = 30;

// 属性分配公式（与后端 RoleService 保持一致）
const ATTR_FORMULA = {
    hp:  { base: 100, perPoint: 10, previewLabel: 'HP'  },
    atk: { base: 10,  perPoint: 2,  previewLabel: 'ATK' },
    def: { base: 0,   perPoint: 1,  previewLabel: 'DEF' },
    mp:  { base: 100, perPoint: 10, previewLabel: 'MP'  }
};

// 战斗入口按钮状态
const BATTLE_BTN = {
    START: { text: '⚔ 开始战斗', cls: 'btn-battle' },
    CONTINUE: { text: '⚔ 继续战斗', cls: 'btn-battle continue-battle' }
};

/* ---------- 全局状态 ---------- */
// 当前角色数据（RoleVO 或 null）
let currentRole = null;

/* ==========================================================
 * 入口
 * ========================================================== */

// 1) 守卫：无 token 跳登录
FG.guard();

// 2) 顶部欢迎语
document.getElementById('welcome-text').textContent = '欢迎，' + FG.getUsername();

// 2.1) 头像：注册/更换后存在本地，显示在名字旁；没有则显示 👤 占位
const avatarImg = document.getElementById('user-avatar');
const avatarPlaceholder = document.getElementById('user-avatar-placeholder');

function renderAvatar(dataUrl) {
    if (dataUrl) {
        avatarImg.src = dataUrl;
        avatarImg.classList.remove('hidden');
        avatarPlaceholder.classList.add('hidden');
    } else {
        avatarImg.src = '';
        avatarImg.classList.add('hidden');
        avatarPlaceholder.classList.remove('hidden');
    }
}

renderAvatar(FG.getAvatar());

// 2.2) 点击头像更换：选图 → 压缩 → 存本地 → 立即刷新顶栏
document.getElementById('btn-avatar').addEventListener('click', function () {
    document.getElementById('avatar-input').click();
});

document.getElementById('avatar-input').addEventListener('change', function (event) {
    const file = event.target.files && event.target.files[0];
    if (!file) return;

    FG.compressImage(file, 128, function (dataUrl) {
        FG.setAvatar(FG.getUsername(), dataUrl);
        renderAvatar(dataUrl);
        FG.toast('头像已更新', 'success');
    }, function (msg) {
        FG.toast(msg, 'error');
    });

    // 清空选择，方便再次选同一张图
    event.target.value = '';
});

// 3) 退出登录绑定（全局一次性）
document.getElementById('btn-logout').addEventListener('click', function () {
    FG.clearAuth();
    location.replace('index.html');
});

// 4) 初始化：拉角色信息
init();

async function init() {
    // 检查 URL 参数：失败后从 battle.html 跳过来重新分配属性点
    const isRecreate = new URLSearchParams(location.search).get('recreate') === '1';

    if (isRecreate) {
        // 直接显示创建面板，从 30 点未分配 / 四项全 0 开始（不预填旧属性）
        showCreatePanel();
        initBattleEntry();
        return;
    }

    try {
        currentRole = await FG.request('GET', '/api/role/info');
        // 200 → 已有角色
        renderRole(currentRole);
    } catch (e) {
        // 后端未创建角色会抛 404 "尚未创建角色"
        // 统一用 message 包含"尚未创建"来判定（避免强耦合 error code）
        if (e.message && e.message.indexOf('尚未创建') !== -1) {
            showCreatePanel();
        } else {
            // 其他错误 → toast + 默认显示创建面板（兜底）
            FG.toast(e.message, 'error');
            showCreatePanel();
        }
    }

    // 初始化战斗入口按钮状态（根据本地 fg_battleId 是否存在）
    initBattleEntry();
}

/* ==========================================================
 * 创建面板：属性点分配器
 * ========================================================== */

/**
 * 显示创建面板（隐藏角色卡）并绑定事件
 */
function showCreatePanel() {
    document.getElementById('create-panel').classList.remove('hidden');
    document.getElementById('role-panel').classList.add('hidden');

    // 初始化分配器状态：30 点未分配，四项全 0
    const state = { hp: 0, atk: 0, def: 0, mp: 0 };
    bindPointsAllocator(state);
    refreshPointsAllocator(state);
}

/**
 * 绑定分配器行的加减按钮（事件委托到父容器）
 */
function bindPointsAllocator(state) {
    const allocator = document.getElementById('points-allocator');

    // 避免重复绑定：解绑旧的
    allocator.onclick = function (e) {
        const target = e.target;
        if (!(target instanceof HTMLButtonElement)) return;

        // 找到最近的 points-row
        const row = target.closest('.points-row');
        if (!row) return;
        const attr = row.dataset.attr; // hp / atk / def / mp
        if (!ATTR_FORMULA[attr]) return;

        if (target.hasAttribute('data-points-plus')) {
            // 加：剩余够才加
            const total = calcTotal(state);
            if (total >= TOTAL_POINTS) return;
            state[attr] += 1;
        } else if (target.hasAttribute('data-points-minus')) {
            // 减：当前 > 0 才减
            if (state[attr] <= 0) return;
            state[attr] -= 1;
        }

        refreshPointsAllocator(state);
    };

    // 创建按钮
    const btnCreate = document.getElementById('btn-create-role');
    btnCreate.onclick = function () {
        doCreateRole(state);
    };
}

/**
 * 刷新分配器 UI：数值、按钮禁用态、预览、剩余、创建按钮可用态
 */
function refreshPointsAllocator(state) {
    const total = calcTotal(state);
    const remaining = TOTAL_POINTS - total;

    // 每一行
    const rows = document.querySelectorAll('.points-row');
    rows.forEach(function (row) {
        const attr = row.dataset.attr;
        const val = state[attr];
        const formula = ATTR_FORMULA[attr];

        // 数值显示
        row.querySelector('.points-val').textContent = val;

        // 预览值
        const previewVal = formula.base + val * formula.perPoint;
        row.querySelector('.preview-num').textContent = previewVal;

        // 减号按钮：val === 0 时禁用
        const minusBtn = row.querySelector('[data-points-minus]');
        minusBtn.disabled = (val <= 0);

        // 加号按钮：remaining === 0 时禁用
        const plusBtn = row.querySelector('[data-points-plus]');
        plusBtn.disabled = (remaining <= 0);
    });

    // 剩余点数
    document.getElementById('remaining-points').textContent = remaining;

    // 创建按钮：合计必须 === 30 才可点
    document.getElementById('btn-create-role').disabled = (total !== TOTAL_POINTS);
}

function calcTotal(state) {
    return state.hp + state.atk + state.def + state.mp;
}

/**
 * 提交创建角色
 */
async function doCreateRole(state) {
    try {
        FG.showLoading();
        const role = await FG.request('POST', '/api/role/create', {
            hpPoints:  state.hp,
            atkPoints: state.atk,
            defPoints: state.def,
            mpPoints:  state.mp
        });
        FG.toast('角色创建成功！', 'success');
        currentRole = role;
        // 清掉 URL 上的 ?recreate=1 参数，避免刷新又回到创建面板
        if (window.history && window.history.replaceState) {
            history.replaceState(null, '', 'game.html');
        }
        renderRole(role);
    } catch (e) {
        // 后端校验失败（如总点数不为 30）的提示原样展示
        FG.toast(e.message, 'error');
    } finally {
        FG.hideLoading();
    }
}

/* ==========================================================
 * 角色信息卡渲染
 * ========================================================== */

/**
 * 显示角色卡（隐藏创建面板），并全量刷新各字段
 * @param {RoleVO} role
 */
function renderRole(role) {
    document.getElementById('create-panel').classList.add('hidden');
    document.getElementById('role-panel').classList.remove('hidden');

    // 顶部：角色名 + 胜场
    document.getElementById('role-name').textContent = role.roleName || FG.getUsername();
    document.getElementById('role-wins').textContent = role.wins || 0;

    // HP
    setBar('hp', role.hp, role.maxHp);
    document.getElementById('attr-maxhp').textContent = role.maxHp;

    // MP
    setBar('mp', role.mp, role.maxMp);
    document.getElementById('attr-maxmp').textContent = role.maxMp;

    // 属性
    document.getElementById('attr-atk').textContent = role.atk;
    document.getElementById('attr-def').textContent = role.def;

    // 技能
    renderSkills(role.skills);

    // 背包
    renderPackage(role.packageList);

    // 保存最新角色到全局
    currentRole = role;

    // 刷新战斗入口按钮状态
    initBattleEntry();
}

/**
 * 设置血条/蓝条（fill width + 数字）
 */
function setBar(type, cur, max) {
    const safeMax = Math.max(max, 1);
    const percent = Math.max(0, Math.min(100, Math.round(cur / safeMax * 100)));

    document.getElementById(type + '-cur').textContent = cur;
    document.getElementById(type + '-max').textContent = max;
    document.getElementById(type + '-bar').style.width = percent + '%';
}

/**
 * 渲染技能标签列表
 */
function renderSkills(skills) {
    const container = document.getElementById('skill-list');
    container.innerHTML = '';

    if (!skills || skills.length === 0) {
        container.innerHTML = '<span class="package-empty">暂无技能</span>';
        return;
    }

    skills.forEach(function (skillName) {
        const tag = document.createElement('span');
        tag.className = 'skill-tag';
        tag.textContent = skillName;
        container.appendChild(tag);
    });
}

/**
 * 渲染背包
 * @param {Array<RoleVO.PackageItem>} packageList 后端保证 count > 0
 */
function renderPackage(packageList) {
    const container = document.getElementById('package-list');
    container.innerHTML = '';

    if (!packageList || packageList.length === 0) {
        container.innerHTML = '<div class="package-empty">背包空空如也～</div>';
        return;
    }

    packageList.forEach(function (item) {
        const row = document.createElement('div');
        row.className = 'package-row';
        row.innerHTML =
            '<span class="p-name"></span>' +
            '<span class="p-count">x1</span>' +
            '<span class="p-effect"></span>' +
            '<button type="button" class="p-use-btn">使用</button>';

        row.querySelector('.p-name').textContent = item.name;
        row.querySelector('.p-count').textContent = 'x' + item.count;
        row.querySelector('.p-effect').textContent = item.effect || '';

        // 使用按钮：数量 > 1 时弹数量选择弹窗，否则直接使用 1 个
        row.querySelector('.p-use-btn').addEventListener('click', function () {
            if (item.count > 1) {
                showItemUseDialog(item);
            } else {
                doUseItem(item.name, 1);
            }
        });

        container.appendChild(row);
    });
}

/**
 * 道具数量选择弹窗（数量 > 1 时弹出）
 * @param {RoleVO.PackageItem} item 包含 name / count / effect
 */
function showItemUseDialog(item) {
    const overlay = document.getElementById('item-use-overlay');
    const infoEl = document.getElementById('item-use-info');
    const qtyEl = document.getElementById('qty-value');
    const minusBtn = document.getElementById('qty-minus');
    const plusBtn = document.getElementById('qty-plus');
    const cancelBtn = document.getElementById('btn-qty-cancel');
    const confirmBtn = document.getElementById('btn-qty-confirm');

    // 填充道具信息
    infoEl.innerHTML =
        '<div class="item-use-name">' + escapeHtml(item.name) + '</div>' +
        '<div class="item-use-meta">当前拥有：x' + item.count + '　效果：' + escapeHtml(item.effect || '') + '</div>';

    // 初始化数量
    let qty = 1;
    refreshQty();

    function refreshQty() {
        qtyEl.textContent = qty;
        minusBtn.disabled = (qty <= 1);
        plusBtn.disabled = (qty >= item.count);
    }

    minusBtn.onclick = function () {
        if (qty > 1) { qty -= 1; refreshQty(); }
    };
    plusBtn.onclick = function () {
        if (qty < item.count) { qty += 1; refreshQty(); }
    };

    cancelBtn.onclick = function () {
        overlay.classList.add('hidden');
    };

    confirmBtn.onclick = async function () {
        overlay.classList.add('hidden');
        await doUseItem(item.name, qty);
    };

    overlay.classList.remove('hidden');
}

/**
 * HTML 转义（防 XSS）
 */
function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/**
 * 使用道具
 * @param {string} itemName 道具名
 * @param {number} count 使用数量
 */
async function doUseItem(itemName, count) {
    try {
        FG.showLoading();
        const role = await FG.request('POST', '/api/item/use', {
            itemName: itemName,
            count: count
        });
        FG.toast('使用成功！', 'success');
        // 用后端返回的最新 RoleVO 整体刷新
        renderRole(role);
    } catch (e) {
        FG.toast(e.message, 'error');
    } finally {
        FG.hideLoading();
    }
}

/* ==========================================================
 * 战斗入口
 * ========================================================== */

/**
 * 初始化战斗入口按钮状态
 * - 本地有 fg_battleId → 先调 GET /api/battle/state 校验是否还在进行中
 *   - ongoing → 按钮文案"继续战斗"
 *   - 已结束或校验失败 → 清 fg_battleId，按钮回"开始战斗"
 * - 本地无 → "开始战斗"
 */
async function initBattleEntry() {
    const btn = document.getElementById('btn-battle');
    const battleId = FG.getBattleId();

    if (!battleId) {
        setBattleBtn(btn, BATTLE_BTN.START);
        bindBattleBtn(btn, 'start');
        return;
    }

    // 有 battleId → 校验
    try {
        const state = await FG.request('GET', '/api/battle/state/' + battleId);
        // battleResult=ongoing 才是可继续的战斗
        if (state && state.battleResult === 'ongoing') {
            setBattleBtn(btn, BATTLE_BTN.CONTINUE);
            bindBattleBtn(btn, 'continue');
        } else {
            // 战斗已结束（victory/defeat），清掉本地 id
            FG.setBattleId('');
            setBattleBtn(btn, BATTLE_BTN.START);
            bindBattleBtn(btn, 'start');
        }
    } catch (e) {
        // 校验失败（战斗不存在/服务端重启丢了）→ 清本地 id
        FG.setBattleId('');
        setBattleBtn(btn, BATTLE_BTN.START);
        bindBattleBtn(btn, 'start');
    }
}

function setBattleBtn(btn, cfg) {
    btn.className = cfg.cls;
    btn.textContent = cfg.text;
}

function bindBattleBtn(btn, mode) {
    btn.onclick = (mode === 'continue') ? doContinueBattle : doStartBattle;
}

/**
 * 开始战斗：POST /api/battle/start → 存 battleId → 跳 battle.html
 */
async function doStartBattle() {
    try {
        FG.showLoading();
        const battle = await FG.request('POST', '/api/battle/start');
        if (battle && battle.battleId) {
            FG.setBattleId(battle.battleId);
        }
        location.href = 'battle.html';
    } catch (e) {
        FG.toast(e.message, 'error');
    } finally {
        FG.hideLoading();
    }
}

/**
 * 继续战斗：直接跳 battle.html，战斗页会用本地 fg_battleId 恢复
 */
function doContinueBattle() {
    location.href = 'battle.html';
}
