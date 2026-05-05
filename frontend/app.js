// SynchPlay frontend — modify API_BASE to switch backend
const API_BASE = 'http://localhost:8080';

function $(id) { return document.getElementById(id); }
function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

// Tab switching
document.querySelectorAll('.tab').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    $(btn.dataset.panel).classList.add('active');
    if (btn.dataset.panel === 'friends') loadFriendUsers();
    if (btn.dataset.panel === 'lcc') loadLcc();
    if (btn.dataset.panel === 'pagerank') loadPageRank();
  });
});

// ── Overview ──
async function loadOverview() {
  try {
    const res = await fetch(API_BASE + '/api/stats');
    const data = await res.json();
    $('connStatus').textContent = 'Connected: ' + API_BASE + ' | ' + data.graph.totalNodes + ' nodes ' + data.graph.totalEdges + ' edges';
    $('statNodes').textContent = data.graph.totalNodes;
    $('statUsers').textContent = data.graph.userNodes;
    $('statVideos').textContent = data.graph.videoNodes;
    $('statEdges').textContent = data.graph.totalEdges;
    $('statDensity').textContent = data.graph.density;
    $('statAvgDeg').textContent = data.graph.avgDegree;
    $('statSocial').textContent = data.edgeTypes.social;
    $('statWatch').textContent = data.edgeTypes.watch;
    $('statSimilar').textContent = data.edgeTypes.similar;
  } catch (e) {
    $('connStatus').textContent = 'Disconnected — is the backend running at ' + API_BASE + '?';
    console.error(e);
  }
}

// ── Users ──
async function loadUsers() {
  try {
    const res = await fetch(API_BASE + '/api/users');
    const users = await res.json();
    const sel = $('userSelect');
    sel.innerHTML = '<option value="">-- Select user (' + users.length + ') --</option>';
    users.forEach(u => {
      sel.innerHTML += '<option value="' + u.id + '">' + u.name + '</option>';
    });
    if (users.length > 0) {
      sel.value = users[0].id;
      $('alphaBeta').style.display = '';
      loadRecommendations();
    }
  } catch (e) { console.error(e); }
}

// ── Friend Recommendations ──
async function loadFriendUsers() {
  try {
    const res = await fetch(API_BASE + '/api/users');
    const users = await res.json();
    const sel = $('friendUserSelect');
    sel.innerHTML = '<option value="">-- Select user (' + users.length + ') --</option>';
    users.forEach(u => {
      sel.innerHTML += '<option value="' + u.id + '">' + u.name + '</option>';
    });
    if (users.length > 0) { sel.value = users[0].id; loadFriendRecommendations(); }
  } catch (e) { console.error(e); }
}

async function loadFriendRecommendations() {
  const userId = $('friendUserSelect').value;
  if (!userId) { $('friendsResult').innerHTML = '<div class="empty">Select a user</div>'; return; }
  $('friendsResult').innerHTML = '<div class="empty">Loading...</div>';
  try {
    const res = await fetch(API_BASE + '/api/friends?userId=' + encodeURIComponent(userId));
    const data = await res.json();
    if (data.error) { $('friendsResult').innerHTML = '<div class="empty">' + data.error + '</div>'; return; }
    if (data.friends.length === 0) { $('friendsResult').innerHTML = '<div class="empty">No friend suggestions found</div>'; return; }
    let html = '<p style="margin-bottom:12px;color:#999">User: ' + data.userId + ' | Suggested: ' + data.totalRecommended + '</p>';
    html += '<table><tr><th>#</th><th>User ID</th><th>Name</th></tr>';
    data.friends.forEach(f => {
      html += '<tr><td>' + f.rank + '</td><td>' + esc(f.userId) + '</td><td>' + esc(f.name) + '</td></tr>';
    });
    html += '</table>';
    $('friendsResult').innerHTML = html;
  } catch (e) { $('friendsResult').innerHTML = '<div class="empty">Load failed</div>'; }
}

// ── Video Recommendations ──
function onUserChange() {
  const userId = $('userSelect').value;
  if (userId) {
    $('alphaBeta').style.display = '';
    loadRecommendations();
  } else {
    $('alphaBeta').style.display = 'none';
    $('recommendSummary').style.display = 'none';
    $('recommendResult').innerHTML = '<div class="empty">Select a user to see recommendations</div>';
  }
}

function onSliderChange() {
  $('alphaVal').textContent = $('alphaSlider').value;
  $('betaVal').textContent = $('betaSlider').value;
  loadRecommendations();
}

async function loadRecommendations() {
  const userId = $('userSelect').value;
  if (!userId) { $('recommendResult').innerHTML = '<div class="empty">Select a user</div>'; return; }
  $('recommendResult').innerHTML = '<div class="empty">Loading...</div>';
  try {
    const alpha = parseFloat($('alphaSlider').value);
    const beta = parseFloat($('betaSlider').value);
    const res = await fetch(API_BASE + '/api/recommend?userId=' + encodeURIComponent(userId) + '&alpha=' + alpha + '&beta=' + beta);
    const data = await res.json();
    if (data.error) { $('recommendResult').innerHTML = '<div class="empty">' + data.error + '</div>'; return; }

    let summary = '<div class="summary-bar">';
    summary += '<div class="summary-item"><div class="val">' + esc(data.userId) + '</div><div class="lbl">User</div></div>';
    summary += '<div class="summary-item"><div class="val">' + data.totalCandidates + '</div><div class="lbl">Candidates</div></div>';
    summary += '<div class="summary-item"><div class="val">&alpha;=' + alpha + ' &beta;=' + beta + '</div><div class="lbl">Weights</div></div>';
    if (data.recommendations.length > 0) {
      const top = data.recommendations[0];
      summary += '<div class="summary-item"><div class="val" style="font-size:16px">' + esc(top.title.substring(0, 25)) + (top.title.length > 25 ? '...' : '') + '</div><div class="lbl">Top Pick</div></div>';
    }
    summary += '</div>';
    $('recommendSummary').innerHTML = summary;
    $('recommendSummary').style.display = '';

    if (data.recommendations.length === 0) {
      $('recommendResult').innerHTML = '<div class="empty">No candidates found</div>';
      return;
    }
    let html = '<div class="video-grid">';
    data.recommendations.slice(0, 12).forEach(r => {
      const videoId = r.videoId.replace('video_', '');
      const thumbUrl = 'https://img.youtube.com/vi/' + videoId + '/mqdefault.jpg';
      const rankClass = r.rank <= 3 ? ' top' : '';
      html += '<div class="video-card">' +
        '<div class="thumb-wrap">' +
          '<span class="rank-num' + rankClass + '">' + r.rank + '</span>' +
          '<img class="thumb" src="' + thumbUrl + '" alt="thumbnail" loading="lazy" ' +
               'onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'">' +
          '<div class="thumb-placeholder" style="display:none">&#9654;</div>' +
        '</div>' +
        '<div class="info">' +
          '<div class="title" title="' + esc(r.title) + '">' + esc(r.title) + '</div>' +
          '<div class="meta"><span>' + esc(r.channel) + '</span><span>' + Number(r.views).toLocaleString() + ' views</span></div>' +
          '<div class="scores">' +
            '<span class="score-tag">dist ' + r.distance + '</span>' +
            '<span class="score-tag">PR ' + r.pageRankScore + '</span>' +
            '<span class="score-tag final">score ' + r.finalScore + '</span>' +
          '</div>' +
        '</div>' +
      '</div>';
    });
    html += '</div>';
    if (data.recommendations.length > 12) {
      html += '<p style="text-align:center;color:#666;margin-top:16px">+ ' + (data.recommendations.length - 12) + ' more</p>';
    }
    $('recommendResult').innerHTML = html;
  } catch (e) {
    $('recommendResult').innerHTML = '<div class="empty">Load failed: ' + esc(e.message) + '</div>';
  }
}

// ── LCC Echo Chamber ──
async function loadLcc() {
  try {
    const res = await fetch(API_BASE + '/api/lcc');
    const data = await res.json();
    let high = 0, med = 0, low = 0;
    data.forEach(u => { if (u.riskLevel === 'high') high++; else if (u.riskLevel === 'medium') med++; else low++; });
    let html = '<div class="cards" style="margin-bottom:16px">' +
      '<div class="card"><div class="num" style="color:#ff4757">' + high + '</div><div class="lbl">High (LCC &gt; 0.7)</div></div>' +
      '<div class="card"><div class="num" style="color:#ffa502">' + med + '</div><div class="lbl">Medium (0.3–0.7)</div></div>' +
      '<div class="card"><div class="num" style="color:#2ed573">' + low + '</div><div class="lbl">Low (&lt; 0.3)</div></div>' +
    '</div>';
    html += '<table><tr><th>#</th><th>User</th><th>LCC</th><th>Risk</th></tr>';
    data.slice(0, 20).forEach((u, i) => {
      const badge = u.riskLevel === 'high' ? 'badge-high' : u.riskLevel === 'medium' ? 'badge-medium' : 'badge-low';
      const label = u.riskLevel === 'high' ? 'HIGH' : u.riskLevel === 'medium' ? 'MED' : 'LOW';
      html += '<tr><td>' + (i + 1) + '</td><td>' + esc(u.name) + '</td>' +
        '<td style="font-weight:600">' + u.lcc + '</td>' +
        '<td><span class="badge ' + badge + '">' + label + '</span></td></tr>';
    });
    html += '</table>';
    $('lccResult').innerHTML = html;
  } catch (e) { $('lccResult').innerHTML = '<div class="empty">Load failed</div>'; }
}

// ── PageRank ──
async function loadPageRank() {
  try {
    const res = await fetch(API_BASE + '/api/pagerank?top=15');
    const data = await res.json();
    let html = '<table><tr><th>#</th><th>Title</th><th>Channel</th><th>Score</th></tr>';
    data.forEach(v => {
      html += '<tr><td>' + v.rank + '</td><td>' + esc(v.title) + '</td><td>' + esc(v.channel) + '</td>' +
        '<td style="color:#667eea;font-weight:600">' + v.score + '</td></tr>';
    });
    html += '</table>';
    $('prResult').innerHTML = html;
  } catch (e) { $('prResult').innerHTML = '<div class="empty">Load failed</div>'; }
}

// Init
$('footerApi').textContent = API_BASE;
loadOverview();
loadUsers();
