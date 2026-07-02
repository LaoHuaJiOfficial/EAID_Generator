const GAMES = {
    BF1: { label: '战地风云 1', file: 'bundles/BF1Bundle.txt' },
    BF4: { label: '战地风云 4', file: 'bundles/BF4Bundle.txt' },
    BF1_AND_4: { label: '战地风云 1&4', file: 'bundles/BF1&4Bundle.txt' },
};

const bundleCache = new Map();
let currentGame = 'BF1';
let worker = null;
let isRunning = false;

const el = {
    game: document.getElementById('game'),
    prefix: document.getElementById('prefix'),
    eaid: document.getElementById('eaid'),
    hash: document.getElementById('hash'),
    limit: document.getElementById('limit'),
    localization: document.getElementById('localization'),
    results: document.getElementById('results'),
    progress: document.getElementById('progress'),
    btnCompute: document.getElementById('btnCompute'),
    btnStop: document.getElementById('btnStop'),
};

function setRunning(running) {
    isRunning = running;
    el.btnCompute.disabled = running;
    el.btnStop.disabled = !running;
    el.btnCompute.textContent = running ? '处理中' : '计算';
}

async function loadBundle(gameKey) {
    if (bundleCache.has(gameKey)) {
        return bundleCache.get(gameKey);
    }
    el.progress.textContent = '正在加载词库…';
    const resp = await fetch(GAMES[gameKey].file);
    if (!resp.ok) {
        throw new Error('加载词库失败: ' + GAMES[gameKey].file);
    }
    const text = await resp.text();
    const textMap = EAIDCore.parseBundle(text);
    const sortedKeys = EAIDCore.buildSortedKeys(textMap);
    const data = { textMap, sortedKeys };
    bundleCache.set(gameKey, data);
    return data;
}

async function switchGame(gameKey) {
    currentGame = gameKey;
    try {
        const { textMap } = await loadBundle(gameKey);
        el.localization.value = EAIDCore.formatLocalization(textMap);
        el.progress.textContent = '已切换至 ' + GAMES[gameKey].label + '，共 ' + textMap.size + ' 条文本';
    } catch (err) {
        el.progress.textContent = err.message;
        el.localization.value = '';
    }
}

function terminateWorker() {
    if (worker) {
        worker.postMessage({ type: 'stop' });
        worker.terminate();
        worker = null;
    }
}

async function startComputation() {
    if (isRunning) return;

    let hashValue;
    let maxResults;
    let bracketPrefix;
    try {
        hashValue = EAIDCore.parseHash(el.hash.value);
        maxResults = EAIDCore.parseMaxResults(el.limit.value);
        bracketPrefix = EAIDCore.parseBracketPrefix(el.prefix.value);
    } catch (err) {
        el.results.textContent = err.message;
        return;
    }

    let bundle;
    try {
        bundle = await loadBundle(currentGame);
    } catch (err) {
        el.results.textContent = err.message;
        return;
    }

    el.results.textContent = '';
    setRunning(true);
    terminateWorker();

    worker = new Worker('worker.js');
    worker.onmessage = (e) => {
        const msg = e.data;
        switch (msg.type) {
            case 'progress':
                el.progress.textContent = '尝试尾部通配符长度: ' + msg.tailLen + '，已找到: ' + msg.found;
                break;
            case 'result':
                appendResult(msg.fullId, msg.computedHash, msg.name);
                break;
            case 'error':
                el.results.textContent = msg.message;
                setRunning(false);
                terminateWorker();
                break;
            case 'done':
                if (msg.count === 0 && !msg.stopped) {
                    el.results.textContent += '\n未找到可行 ID\n';
                } else if (msg.bracketPrefix) {
                    el.results.textContent += '\n计算使用前缀: ' + msg.bracketPrefix + '\n';
                }
                el.results.textContent += '\n任务已完成\n';
                el.progress.textContent = msg.stopped ? '已终止' : '计算完成，共 ' + msg.count + ' 个结果';
                setRunning(false);
                terminateWorker();
                break;
        }
    };
    worker.onerror = (err) => {
        el.results.textContent = 'Worker 错误: ' + err.message;
        setRunning(false);
        terminateWorker();
    };

    const textMapEntries = Array.from(bundle.textMap.entries());
    worker.postMessage({
        type: 'search',
        bracketPrefix,
        idBase: el.eaid.value,
        hashValue,
        maxResults,
        textMapEntries,
        sortedKeys: bundle.sortedKeys,
    });
}

function appendResult(fullId, computedHash, name) {
    const hashHex = computedHash.toString(16).toUpperCase().padStart(8, '0');
    el.results.textContent += '\n查询 ' + fullId + '  Hash：' + hashHex + '  中文ID：' + name;
    el.results.scrollTop = el.results.scrollHeight;
}

function stopComputation() {
    if (!isRunning) return;
    terminateWorker();
    el.results.textContent = '';
    el.progress.textContent = '已终止';
    setRunning(false);
}

function init() {
    for (const [key, cfg] of Object.entries(GAMES)) {
        const opt = document.createElement('option');
        opt.value = key;
        opt.textContent = cfg.label;
        el.game.appendChild(opt);
    }
    el.limit.value = String(EAIDCore.DEFAULT_MAX_RESULTS);
    el.game.value = currentGame;

    el.game.addEventListener('change', () => switchGame(el.game.value));
    el.btnCompute.addEventListener('click', startComputation);
    el.btnStop.addEventListener('click', stopComputation);

    switchGame(currentGame);
    setRunning(false);
}

init();
