/* ==========================================================
 * 公共基础层
 * ----------------------------------------------------------
 * 职责：
 *   1. FG 常量：BASE_URL、localStorage 键
 *   2. FG.request()：统一 fetch 封装（鉴权头、code 分支、401 自动跳登录）
 *   3. token 管理：getToken / setToken / clearAuth / getUsername
 *   4. 页面守卫 guard()：无 token 直接跳登录页
 *   5. UI 工具：toast / showLoading / hideLoading / confirmDialog
 *
 * 与后端的约定：
 *   - 后端所有接口 HTTP 状态码均为 200，业务结果看 body.code
 *   - 401 统一清 token 跳 index，调用方无感知
 *   - 匿名接口（验证码/登录/注册/忘记密码）需传 needAuth=false
 * ========================================================== */

// ---------- 常量 ----------
const FG = {
    // 同源部署，BASE_URL 留空即可；若后端部署在别处可填完整地址
    BASE_URL: '',
    // localStorage 键约定
    TOKEN_KEY: 'fg_token',
    USERNAME_KEY: 'fg_username',
    BATTLE_ID_KEY: 'fg_battleId'
};

// ---------- token 管理 ----------

/** 读取本地 token */
FG.getToken = function () {
    return localStorage.getItem(FG.TOKEN_KEY) || '';
};

/** 写入 token */
FG.setToken = function (token) {
    localStorage.setItem(FG.TOKEN_KEY, token);
};

/** 读取本地用户名 */
FG.getUsername = function () {
    return localStorage.getItem(FG.USERNAME_KEY) || '';
};

/** 写入用户名 */
FG.setUsername = function (username) {
    localStorage.setItem(FG.USERNAME_KEY, username);
};

/** 读取进行中战斗 id */
FG.getBattleId = function () {
    return localStorage.getItem(FG.BATTLE_ID_KEY) || '';
};

/** 写入/清除进行中战斗 id */
FG.setBattleId = function (battleId) {
    if (battleId) {
        localStorage.setItem(FG.BATTLE_ID_KEY, battleId);
    } else {
        localStorage.removeItem(FG.BATTLE_ID_KEY);
    }
};

/** 头像 localStorage 键前缀：按用户名分别保存，避免同机换账号串头像 */
FG.AVATAR_KEY_PREFIX = 'fg_avatar_';

/** 读取头像 data URL（不传用户名则读当前登录用户），没有返回空串 */
FG.getAvatar = function (username) {
    const name = username || FG.getUsername();
    if (!name) return '';
    return localStorage.getItem(FG.AVATAR_KEY_PREFIX + name) || '';
};

/** 保存头像 data URL（注册成功时调用） */
FG.setAvatar = function (username, dataUrl) {
    if (!username || !dataUrl) return;
    localStorage.setItem(FG.AVATAR_KEY_PREFIX + username, dataUrl);
};

/**
 * 把图片文件压缩成 data URL（统一 JPEG 质量 0.8，最长边缩到 maxEdge）
 * 注册页选头像、主页换头像都复用它
 * @param {File} file - 用户选择的文件
 * @param {number} maxEdge - 最长边像素上限，如 128
 * @param {Function} onSuccess - 成功回调，参数为 data URL
 * @param {Function} onError - 失败回调，参数为提示文字
 */
FG.compressImage = function (file, maxEdge, onSuccess, onError) {
    if (!file || !file.type || file.type.indexOf('image/') !== 0) {
        onError('请选择图片文件（JPG / PNG）');
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        onError('图片不能超过 5MB');
        return;
    }

    const reader = new FileReader();
    reader.onerror = function () {
        onError('图片读取失败，请重试');
    };
    reader.onload = function () {
        const img = new Image();
        img.onerror = function () {
            onError('图片解析失败，请换一张');
        };
        img.onload = function () {
            // 等比缩放到最长边 maxEdge（小图不放大）
            const scale = Math.min(1, maxEdge / Math.max(img.width, img.height));
            const width = Math.round(img.width * scale);
            const height = Math.round(img.height * scale);

            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            canvas.getContext('2d').drawImage(img, 0, 0, width, height);

            onSuccess(canvas.toDataURL('image/jpeg', 0.8));
        };
        img.src = reader.result;
    };
    reader.readAsDataURL(file);
};

/** 清除所有鉴权相关本地数据（退出登录 / 401 时调用） */
FG.clearAuth = function () {
    localStorage.removeItem(FG.TOKEN_KEY);
    localStorage.removeItem(FG.USERNAME_KEY);
    localStorage.removeItem(FG.BATTLE_ID_KEY);
};

// ---------- 统一请求封装 ----------

/**
 * 通用请求方法
 * @param {string} method  - HTTP 方法：GET / POST ...
 * @param {string} url     - 接口路径，如 '/api/user/login'
 * @param {object} [body]  - POST 请求体（GET 时传 null 或省略）
 * @param {boolean} [needAuth=true] - 是否需要带 token（验证码/登录/注册/忘记密码传 false）
 * @returns {Promise<*>} - 成功时返回 data 字段值；失败时抛 Error
 *
 * 行为约定：
 *   code=200  → resolve(data)，若 data 为空则 resolve(message)
 *   code=401  → clearAuth + location.replace('index.html')，并 reject
 *   code=400/403/404/500 → reject(new Error(message))
 *   网络异常  → reject(new Error('网络异常，请确认后端服务已启动'))
 */
FG.request = async function (method, url, body, needAuth) {
    // 参数默认值
    if (needAuth === undefined) needAuth = true;

    const fullUrl = FG.BASE_URL + url;

    // 组装请求头
    const headers = {
        'Content-Type': 'application/json'
    };
    if (needAuth) {
        const token = FG.getToken();
        if (!token) {
            // 需要鉴权但本地无 token → 视同 401
            FG.clearAuth();
            location.replace('index.html');
            throw new Error('登录已失效，请重新登录');
        }
        headers['Authorization'] = token;
    }

    // fetch 配置
    const options = {
        method: method,
        headers: headers
    };
    if (body !== undefined && body !== null) {
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(fullUrl, options);
    } catch (e) {
        // 网络层异常（后端未启动 / DNS / 跨域等）
        throw new Error('网络异常，请确认后端服务已启动');
    }

    // 尝试解析 JSON（后端所有成功/失败响应都是 JSON）
    let json;
    try {
        json = await response.json();
    } catch (e) {
        // 非 JSON 响应（如后端崩了返回 HTML 错误页）
        throw new Error('服务器返回格式异常');
    }

    const code = json.code;
    const message = json.message || '';
    const data = json.data;

    if (code === 200) {
        // 成功：有 data 返回 data，无 data 返回 message（如注册成功提示语）
        return (data !== null && data !== undefined) ? data : message;
    }

    if (code === 401) {
        // 未授权：清本地并跳登录
        FG.clearAuth();
        location.replace('index.html');
        throw new Error(message || '登录已失效，请重新登录');
    }

    // 其余错误码：原样抛出 message，由调用方展示
    throw new Error(message || ('请求失败（code=' + code + '）'));
};

// ---------- 页面守卫 ----------

/**
 * 受保护页面入口调用：无 token 直接跳登录页
 * 使用 location.replace 避免用户点返回又回到受保护页
 */
FG.guard = function () {
    if (!FG.getToken()) {
        location.replace('index.html');
    }
};

// ---------- UI 工具 ----------

/**
 * 顶部浮层提示（toast）
 * @param {string} msg   - 提示文字
 * @param {string} [type='info'] - 类型：success / error / info（CSS 类名前缀）
 *
 * 实现说明：纯 JS 创建 DOM 挂到 body，样式见 style.css 的 .fg-toast*
 */
FG.toast = function (msg, type) {
    type = type || 'info';

    // 若已存在同名 toast 先移除（防堆积）
    const existing = document.querySelector('.fg-toast');
    if (existing) existing.remove();

    const el = document.createElement('div');
    el.className = 'fg-toast fg-toast-' + type;
    el.textContent = msg;
    document.body.appendChild(el);

    // 2 秒后自动移除
    setTimeout(function () {
        if (el.parentNode) el.parentNode.removeChild(el);
    }, 2000);
};

/** 显示全屏 loading 遮罩（防止重复提交） */
FG.showLoading = function () {
    // 避免重复创建
    if (document.getElementById('fg-loading')) return;

    const mask = document.createElement('div');
    mask.id = 'fg-loading';
    mask.className = 'fg-loading';
    mask.innerHTML = '<div class="fg-loading-spinner">加载中...</div>';
    document.body.appendChild(mask);
};

/** 隐藏 loading 遮罩 */
FG.hideLoading = function () {
    const mask = document.getElementById('fg-loading');
    if (mask) mask.remove();
};

/**
 * 确认弹窗（放弃战斗、重开角色等二次确认）
 * @param {string} msg - 确认文案
 * @returns {Promise<boolean>} - 用户点确定返回 true，取消返回 false
 *
 * 说明：直接复用浏览器原生 confirm，零依赖；包一层 Promise 方便调用方 await
 */
FG.confirmDialog = function (msg) {
    return Promise.resolve(window.confirm(msg));
};

// ---------- 调试辅助（开发阶段用，上线可注释） ----------
// 在浏览器控制台输入 FG 可查看所有公共方法
window.FG = FG;
