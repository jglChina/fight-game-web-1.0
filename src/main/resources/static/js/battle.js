/* ==========================================================
 * 战斗页逻辑
 * ----------------------------------------------------------
 * 职责：
 *   1. 页面守卫 + 断点续战（fg_battleId → state 校验，无则 start）
 *   2. renderState(vo)：回合数、双方血条/蓝条、防御姿态、日志整段重绘
 *   3. 日志分色：💥+你对→蓝绿 / 💥+敌人名→红 / 🏆🎁⚔️→金 / 其余→灰
 *   4. 行动按钮：普通攻击 / 强力一击(10MP) / 生命汲取(10MP) / 使用道具
 *      - 蓝量不足置灰+提示、背包为空置灰、提交期间全部禁用
 *   5. 道具弹窗：GET /api/item/list 取最新背包
 *   6. 结算层：胜利 → rewards 明细 + [下一场][回主页]
 *              失败 → taunt + [重新创建角色][退出到主界面]
 *   7. 放弃战斗：POST /api/battle/abandon
 * ========================================================== */

// ---------- 入口 ----------
FG.guard();

// 当前战斗的 BattleVO（用于渲染期间暂存，结算层按钮要用）
let currentBattle = null;

init();

// ---------- 初始化流程 ----------

/**
 * 进入页面 → 取 fg_battleId
 *   有 → GET /api/battle/state/{id} → 校验进行中 → 渲染
 *   无 / 已结束 → POST /api/battle/start → 存 id → 渲染
 */
async function init() {
    FG.showLoading();
    try {
        const bid = FG.getBattleId();
        let vo = null;

        if (bid) {
            try {
                vo = await FG.request('GET', '/api/battle/state/' + bid);
                if (vo.battleResult !== 'ongoing') {
                    // 上一场已结算：清本地 id 后直接渲染结算层（不再开新局）
                    FG.setBattleId('');
                    currentBattle = vo;
                    renderState(vo);
                    showResult(vo);
                    bindGlobalEvents();
                    return;
                }
            } catch (e) {
                // battleId 已失效（战斗不存在 / 已结束） → 清掉，走新开局
                FG.setBattleId('');
                vo = null;
            }
        }

        // 无本地 id 或已失效：开新局
        if (!vo) {
            vo = await FG.request('POST', '/api/battle/start');
            FG.setBattleId(vo.battleId);
        }

        currentBattle = vo;
        renderState(vo);
        bindGlobalEvents();
    } catch (e) {
        FG.toast(e.message || '初始化失败', 'error');
        // 初始化失败时给用户留回主页的退路
        setTimeout(function () { location.replace('game.html'); }, 1500);
    } finally {
        FG.hideLoading();
    }
}

// ---------- 状态渲染 renderState(vo) ----------

/**
 * 整页重绘：回合数 / 我方 HP MP / 敌方 HP + 防御标记 / 双方属性 / 日志整段重绘
 * 关键：logs 是累计数组，每次整体覆盖渲染（不要 append，否则会重复）
 */
function renderState(vo) {
    // 回合数
    document.getElementById('round-text').textContent = '第 ' + vo.round + ' 回合';

    // 我方状态
    document.getElementById('player-name').textContent = FG.getUsername() || '我';

    // 我方头像：注册/更换后存 localStorage，有头像就用它替换己方大图标 🛡️
    const playerAvatar = FG.getAvatar();
    if (playerAvatar) {
        const avatarIcon = document.getElementById('player-avatar-icon');
        avatarIcon.src = playerAvatar;
        avatarIcon.classList.remove('hidden');
        document.getElementById('player-emoji').classList.add('hidden');
    }

    setBar('player-hp', vo.player.hp, vo.player.maxHp);
    setBar('player-mp', vo.player.mp, vo.player.maxMp);

    // ATK / DEF 每回合都渲染后端最新值（含每 3 胜成长）
    document.getElementById('player-atk').textContent = vo.player.atk;
    document.getElementById('player-def').textContent = vo.player.def;

    // 敌方状态
    document.getElementById('enemy-name').textContent = vo.enemy.name;
    renderEnemyIcon(vo.enemy.name);
    document.getElementById('enemy-atk').textContent = vo.enemy.atk;
    document.getElementById('enemy-def').textContent = vo.enemy.def;
    setBar('enemy-hp', vo.enemy.hp, vo.enemy.maxHp);

    // 防御姿态标记
    const defendTag = document.getElementById('enemy-defending-tag');
    if (vo.enemy.defending) {
        defendTag.classList.remove('hidden');
    } else {
        defendTag.classList.add('hidden');
    }

    // 日志整段重绘
    renderLogs(vo.logs || [], vo.enemy.name);

    // 行动按钮可用性
    updateActionButtons(vo);
}

/**
 * 设置血条/蓝条（与 game.js 同款逻辑）
 * @param {string} prefix - id 前缀（player-hp / player-mp / enemy-hp）
 * @param {number} cur - 当前值
 * @param {number} max - 最大值
 */
function setBar(prefix, cur, max) {
    const safeMax = Math.max(max, 1);
    const percent = Math.max(0, Math.min(100, Math.round(cur / safeMax * 100)));

    document.getElementById(prefix + '-cur').textContent = cur;
    document.getElementById(prefix + '-max').textContent = max;
    document.getElementById(prefix + '-bar').style.width = percent + '%';
}

/**
 * 敌人名 → 图片路径映射（图片放在 static/images/enemies/ 下）
 */
function enemyImage(name) {
    const map = {
        '糖人豆包': '/images/enemies/doubao.webp',
        '奶娃坦克': '/images/enemies/naiwa.jpg',
        '大肥鱼': '/images/enemies/fatfish.webp',
        '鱼人沃特': '/images/enemies/walter.jpg'
    };
    return map[name] || '';
}

/**
 * 渲染敌方图标：有对应图片就显示图片并隐藏 emoji，没有则退回默认 👹
 */
function renderEnemyIcon(name) {
    const img = document.getElementById('enemy-avatar-icon');
    const emoji = document.getElementById('enemy-emoji');
    const src = enemyImage(name);

    if (src) {
        img.src = src;
        img.classList.remove('hidden');
        emoji.classList.add('hidden');
    } else {
        img.src = '';
        img.classList.add('hidden');
        emoji.classList.remove('hidden');
        emoji.textContent = '👹';
    }
}

// ---------- 日志分色渲染 ----------

/**
 * 整段重绘日志，按前缀分色：
 *   💥+"你对"     → 我方蓝绿（log-player）
 *   💥+敌人名     → 敌方红（log-enemy）
 *   🏆 / 🎁 / ⚔️  → 金色高亮（log-gold）
 *   其余           → 灰色（log-system）
 * 渲染完自动滚到底部
 */
function renderLogs(logs, enemyName) {
    const container = document.getElementById('battle-log');
    container.innerHTML = '';

    logs.forEach(function (line) {
        const div = document.createElement('div');
        div.className = 'log-line ' + logColorClass(line, enemyName);
        div.textContent = line;
        container.appendChild(div);
    });

    // 自动滚到底部
    container.scrollTop = container.scrollHeight;
}

function logColorClass(line, enemyName) {
    if (line.startsWith('💥')) {
        if (line.indexOf('你对') !== -1) return 'log-player';
        if (enemyName && line.indexOf(enemyName) !== -1) return 'log-enemy';
    }
    if (line.startsWith('🏆') || line.startsWith('🎁') || line.startsWith('⚔️')) {
        return 'log-gold';
    }
    return 'log-system';
}

// ---------- 行动按钮逻辑 ----------

function bindGlobalEvents() {
    document.getElementById('btn-attack').addEventListener('click', function () {
        doAction({ actionType: 'attack' });
    });
    document.getElementById('btn-skill-heavy').addEventListener('click', function () {
        doAction({ actionType: 'skill', skillIndex: 1 });
    });
    document.getElementById('btn-skill-drain').addEventListener('click', function () {
        doAction({ actionType: 'skill', skillIndex: 2 });
    });
    document.getElementById('btn-item').addEventListener('click', showItemPicker);
    document.getElementById('btn-abandon').addEventListener('click', onAbandon);
    document.getElementById('btn-item-cancel').addEventListener('click', hideItemPicker);
}

/**
 * 根据当前 BattleVO 刷新行动按钮可用性：
 *   - battleResult 非 ongoing → 全部禁用
 *   - 蓝量 < 10 → 强力一击/生命汲取置灰 + 提示
 *   - 背包是否为空 → 道具按钮置灰（背包信息需要拉一次）
 */
async function updateActionButtons(vo) {
    const ongoing = (vo.battleResult === 'ongoing');
    const enoughMp = (vo.player.mp >= 10);

    const btnAttack = document.getElementById('btn-attack');
    const btnHeavy = document.getElementById('btn-skill-heavy');
    const btnDrain = document.getElementById('btn-skill-drain');
    const btnItem = document.getElementById('btn-item');

    btnAttack.disabled = !ongoing;

    // 蓝不足 → 置灰 + 按钮提示
    setSkillBtnState(btnHeavy, ongoing, enoughMp);
    setSkillBtnState(btnDrain, ongoing, enoughMp);

    // 背包为空 → 置灰（异步查一次背包，避免每次渲染都查）
    if (!ongoing) {
        btnItem.disabled = true;
    } else {
        try {
            const role = await FG.request('GET', '/api/item/list');
            const hasItem = role.packageList && role.packageList.length > 0;
            btnItem.disabled = !hasItem;
            btnItem.title = hasItem ? '' : '背包空空如也';
        } catch (e) {
            btnItem.disabled = true;
        }
    }
}

/**
 * 设置技能按钮禁用态 + 提示文案
 */
function setSkillBtnState(btn, ongoing, enoughMp) {
    if (!ongoing) {
        btn.disabled = true;
        btn.title = '';
        return;
    }
    if (!enoughMp) {
        btn.disabled = true;
        btn.title = '蓝量不足（需要 10 MP）';
    } else {
        btn.disabled = false;
        btn.title = '';
    }
}

/**
 * 统一行动入口：禁用全部按钮 → POST action → renderState → 按 battleResult 分支
 */
async function doAction(payload) {
    if (!currentBattle) return;

    // 记录行动前血量，用于判断谁受到伤害（触发受击抖动）
    const prevPlayerHp = currentBattle.player ? currentBattle.player.hp : null;
    const prevEnemyHp = currentBattle.enemy ? currentBattle.enemy.hp : null;

    setAllActionButtons(true);
    FG.showLoading();

    try {
        const body = Object.assign({ battleId: currentBattle.battleId }, payload);
        const vo = await FG.request('POST', '/api/battle/action', body);

        currentBattle = vo;
        renderState(vo);

        // 受击抖动：血量下降的一侧抖一下
        if (prevPlayerHp !== null && vo.player && vo.player.hp < prevPlayerHp) {
            shakeElement(document.getElementById('player-area'));
        }
        if (prevEnemyHp !== null && vo.enemy && vo.enemy.hp < prevEnemyHp) {
            shakeElement(document.getElementById('enemy-area'));
        }

        if (vo.battleResult === 'victory') {
            FG.setBattleId('');
            showResult(vo);
        } else if (vo.battleResult === 'defeat') {
            FG.setBattleId('');
            showResult(vo);
        }
        // ongoing → 按钮在 renderState → updateActionButtons 里已恢复
    } catch (e) {
        FG.toast(e.message || '行动失败', 'error');
        // 失败时也要恢复按钮（按当前状态重新算一次）
        if (currentBattle) updateActionButtons(currentBattle);
    } finally {
        FG.hideLoading();
    }
}

/** 播放受击抖动（动画结束自动移除 class，保证连续触发也能重播） */
function shakeElement(el) {
    if (!el) return;
    el.classList.remove('shake');
    // 强制回流，让动画可以重新播放
    void el.offsetWidth;
    el.classList.add('shake');
    el.addEventListener('animationend', function handler() {
        el.classList.remove('shake');
        el.removeEventListener('animationend', handler);
    });
}

function setAllActionButtons(disabled) {
    document.querySelectorAll('.btn-action').forEach(function (b) {
        b.disabled = disabled;
    });
}

// ---------- 道具弹窗 ----------

/**
 * 点击 [使用道具] → 拉最新背包 → 列出 name ×count + effect → 点使用直接调 action
 */
async function showItemPicker() {
    FG.showLoading();
    try {
        const role = await FG.request('GET', '/api/item/list');
        const list = (role && role.packageList) || [];

        const listEl = document.getElementById('item-picker-list');
        listEl.innerHTML = '';

        if (list.length === 0) {
            listEl.innerHTML = '<div class="package-empty">背包空空如也</div>';
        } else {
            list.forEach(function (item) {
                const row = document.createElement('div');
                row.className = 'item-picker-row';

                // 数量 > 1 时插入行内 +/- 数量选择器；否则保持简洁（直接使用 1 个）
                if (item.count > 1) {
                    row.innerHTML =
                        '<span class="ip-name">' + escapeHtml(item.name) + '</span>' +
                        '<span class="ip-count">×' + item.count + '</span>' +
                        '<span class="ip-effect">' + escapeHtml(item.effect) + '</span>' +
                        '<span class="qty-picker-inline">' +
                            '<button type="button" class="qty-btn-sm qty-minus">−</button>' +
                            '<span class="qty-value-sm">1</span>' +
                            '<button type="button" class="qty-btn-sm qty-plus">+</button>' +
                        '</span>' +
                        '<button type="button" class="ip-use-btn">使用</button>';

                    const qtyEl = row.querySelector('.qty-value-sm');
                    const minusBtn = row.querySelector('.qty-minus');
                    const plusBtn = row.querySelector('.qty-plus');
                    let qty = 1;

                    const refreshQty = function () {
                        qtyEl.textContent = qty;
                        minusBtn.disabled = (qty <= 1);
                        plusBtn.disabled = (qty >= item.count);
                    };
                    minusBtn.addEventListener('click', function () {
                        if (qty > 1) { qty -= 1; refreshQty(); }
                    });
                    plusBtn.addEventListener('click', function () {
                        if (qty < item.count) { qty += 1; refreshQty(); }
                    });
                    refreshQty();

                    row.querySelector('.ip-use-btn').addEventListener('click', function () {
                        hideItemPicker();
                        doAction({ actionType: 'item', itemName: item.name, count: qty });
                    });
                } else {
                    row.innerHTML =
                        '<span class="ip-name">' + escapeHtml(item.name) + '</span>' +
                        '<span class="ip-count">×' + item.count + '</span>' +
                        '<span class="ip-effect">' + escapeHtml(item.effect) + '</span>' +
                        '<button type="button" class="ip-use-btn">使用</button>';

                    row.querySelector('.ip-use-btn').addEventListener('click', function () {
                        hideItemPicker();
                        doAction({ actionType: 'item', itemName: item.name, count: 1 });
                    });
                }

                listEl.appendChild(row);
            });
        }

        document.getElementById('item-picker-overlay').classList.remove('hidden');
    } catch (e) {
        FG.toast(e.message || '获取背包失败', 'error');
    } finally {
        FG.hideLoading();
    }
}

function hideItemPicker() {
    document.getElementById('item-picker-overlay').classList.add('hidden');
}

// ---------- 结算层 ----------

/**
 * victory → 显示 rewards 明细 + [下一场][回主页]
 * defeat  → 显示 taunt + [重新创建角色][退出到主界面]
 */
function showResult(vo) {
    const overlay = document.getElementById('result-overlay');
    const card = document.getElementById('result-card');

    if (vo.battleResult === 'victory') {
        card.className = 'overlay-card overlay-victory';
        card.innerHTML = buildVictoryHtml(vo.rewards);
        bindVictoryButtons(card);
    } else if (vo.battleResult === 'defeat') {
        card.className = 'overlay-card overlay-defeat';
        card.innerHTML = buildDefeatHtml(vo.taunt);
        bindDefeatButtons(card);
    } else {
        return;
    }

    overlay.classList.remove('hidden');
}

function buildVictoryHtml(rewards) {
    if (!rewards) {
        return '<div class="overlay-title">🏆 胜利！</div>' +
               '<div class="overlay-actions">' +
               '<button type="button" id="btn-next-battle" class="btn-overlay-primary">下一场</button>' +
               '<button type="button" id="btn-back-home" class="btn-overlay-secondary">回主页</button>' +
               '</div>';
    }

    let html = '<div class="overlay-title">🏆 胜利！</div>';
    html += '<div class="reward-list">';
    html += '<div class="reward-row"><span class="reward-icon">💗</span>回复 ' + rewards.healAmount + ' 点生命</div>';
    html += '<div class="reward-row"><span class="reward-icon">💙</span>回复 ' + rewards.mpRecover + ' 点蓝量</div>';

    if (rewards.growth && rewards.growthDetail) {
        const g = rewards.growthDetail;
        html += '<div class="reward-row growth-row"><span class="reward-icon">🌟</span>属性成长！</div>';
        html += '<div class="growth-detail">';
        if (g.hpUp) html += '<div class="growth-item">❤️ HP 上限 +' + g.hpUp + '</div>';
        if (g.mpUp) html += '<div class="growth-item">💧 MP 上限 +' + g.mpUp + '</div>';
        if (g.atkUp) html += '<div class="growth-item">⚔️ ATK +' + g.atkUp + '</div>';
        if (g.defUp) html += '<div class="growth-item">🛡️ DEF +' + g.defUp + '</div>';
        html += '</div>';
    }

    if (rewards.droppedItem) {
        html += '<div class="reward-row"><span class="reward-icon">🎁</span>获得道具：' +
                escapeHtml(rewards.droppedItem.name) +
                '（' + escapeHtml(rewards.droppedItem.effect) + '）</div>';
    }

    html += '<div class="reward-row wins-row"><span class="reward-icon">🏆</span>累计胜场 ' + rewards.wins + '</div>';
    html += '</div>';

    html += '<div class="overlay-actions">' +
            '<button type="button" id="btn-next-battle" class="btn-overlay-primary">下一场</button>' +
            '<button type="button" id="btn-back-home" class="btn-overlay-secondary">回主页</button>' +
            '</div>';
    return html;
}

function buildDefeatHtml(taunt) {
    let html = '<div class="overlay-title">💀 失败...</div>';
    html += '<div class="taunt-text">🗯️ ' + escapeHtml(taunt || '再接再厉！') + '</div>';
    html += '<div class="overlay-actions">' +
            '<button type="button" id="btn-retry-fight" class="btn-overlay-primary">⚔️ 不服再战</button>' +
            '<button type="button" id="btn-retry-recreate" class="btn-overlay-secondary">🛠️ 重新分配属性</button>' +
            '</div>';
    return html;
}

function bindVictoryButtons(card) {
    card.querySelector('#btn-next-battle').addEventListener('click', async function () {
        // 下一场：直接 POST /api/battle/start 开新局
        FG.showLoading();
        try {
            const vo = await FG.request('POST', '/api/battle/start');
            FG.setBattleId(vo.battleId);
            currentBattle = vo;
            document.getElementById('result-overlay').classList.add('hidden');
            renderState(vo);
        } catch (e) {
            FG.toast(e.message || '开局失败', 'error');
        } finally {
            FG.hideLoading();
        }
    });
    card.querySelector('#btn-back-home').addEventListener('click', function () {
        location.href = 'game.html';
    });
}

function bindDefeatButtons(card) {
    card.querySelector('#btn-retry-fight').addEventListener('click', async function () {
        await onRetryFight();
    });
    card.querySelector('#btn-retry-recreate').addEventListener('click', async function () {
        await onRetryRecreate();
    });
}

/**
 * 不服再战：保留角色，原地开新战斗（回合数清零）
 * 流程：retry choice:2 清战斗+重置连胜 → POST /api/battle/start → 渲染新战斗
 */
async function onRetryFight() {
    FG.showLoading();
    try {
        await FG.request('POST', '/api/battle/retry', { choice: 2 });
        const vo = await FG.request('POST', '/api/battle/start');
        FG.setBattleId(vo.battleId);
        currentBattle = vo;
        document.getElementById('result-overlay').classList.add('hidden');
        renderState(vo);
    } catch (e) {
        FG.toast(e.message || '开局失败', 'error');
    } finally {
        FG.hideLoading();
    }
}

/**
 * 重新分配属性：清战斗 → 跳 game.html?recreate=1 进创建面板（从四项全 0 重新分配）
 */
async function onRetryRecreate() {
    FG.showLoading();
    try {
        await FG.request('POST', '/api/battle/retry', { choice: 1 });
        location.href = 'game.html?recreate=1';
    } catch (e) {
        FG.toast(e.message || '操作失败', 'error');
    } finally {
        FG.hideLoading();
    }
}

// ---------- 放弃战斗 ----------

async function onAbandon() {
    if (!currentBattle) {
        location.href = 'game.html';
        return;
    }

    const ok = await FG.confirmDialog('确定要放弃当前战斗吗？');
    if (!ok) return;

    FG.showLoading();
    try {
        await FG.request('POST', '/api/battle/abandon', { battleId: currentBattle.battleId });
        FG.setBattleId('');
        location.href = 'game.html';
    } catch (e) {
        FG.toast(e.message || '放弃失败', 'error');
    } finally {
        FG.hideLoading();
    }
}

// ---------- 工具：HTML 转义（防注入） ----------

function escapeHtml(s) {
    if (s === null || s === undefined) return '';
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
