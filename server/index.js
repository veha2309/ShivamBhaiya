const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const zlib = require('zlib');

const app = express();
const PORT = 3000;

app.use(express.json());

// Pending command the app will pick up on next poll
let pendingCommand = null;

// ── Storage ──────────────────────────────────────────────────────────────────
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const dir = path.join(__dirname, 'received');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir);
    cb(null, dir);
  },
  filename: (req, file, cb) => {
    const ts = new Date().toISOString().replace(/[:.]/g, '-');
    cb(null, `${ts}_${file.originalname}`);
  },
});
const upload = multer({ storage });

// ── POST /upload ─────────────────────────────────────────────────────────────
app.post('/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file received' });

  const savedPath = req.file.path;
  console.log(`\n📥 Received: ${req.file.originalname} (${req.file.size} bytes)`);
  console.log(`   Saved to: ${savedPath}`);

  if (req.file.originalname.endsWith('.gz')) {
    const compressed = fs.readFileSync(savedPath);
    zlib.gunzip(compressed, (err, buffer) => {
      if (err) return console.log('   Could not decompress:', err.message);
      try {
        const data = JSON.parse(buffer.toString('utf8'));
        console.log(`\n📋 ${req.file.originalname} (${data.length} records):`);
        console.log(JSON.stringify(data, null, 2));
      } catch {
        console.log('   Raw:', buffer.toString('utf8').slice(0, 500));
      }
    });
  }

  res.json({ success: true, file: req.file.originalname });
});

// ── GET /command — app polls this every 15 min ────────────────────────────────
// Returns: "collect" | "media" | "all" | "" (empty = nothing to do)
app.get('/command', (req, res) => {
  const cmd = pendingCommand || '';
  pendingCommand = null; // consume it
  res.send(cmd);
});

// ── POST /trigger — YOU call this to push a command to the app ───────────────
// Body: { "command": "all" }   (collect | media | all)
app.post('/trigger', (req, res) => {
  const { command } = req.body;
  if (!['collect', 'media', 'all'].includes(command)) {
    return res.status(400).json({ error: 'command must be collect | media | all' });
  }
  pendingCommand = command;
  console.log(`\n🚀 Command queued: ${command}`);
  res.json({ queued: command });
});

// ── GET /files ────────────────────────────────────────────────────────────────
app.get('/files', (req, res) => {
  const dir = path.join(__dirname, 'received');
  if (!fs.existsSync(dir)) return res.json([]);
  const files = fs.readdirSync(dir).map(name => {
    const stat = fs.statSync(path.join(dir, name));
    return { name, size: stat.size, received: stat.mtime };
  });
  res.json(files);
});

app.get('/files/:name', (req, res) => {
  const filePath = path.join(__dirname, 'received', req.params.name);
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'Not found' });
  res.download(filePath);
});

app.listen(PORT, '0.0.0.0', () => {
  console.log('═══════════════════════════════════════════════');
  console.log(`  Server: http://0.0.0.0:${PORT}`);
  console.log(`  Trigger app:  POST /trigger  { "command": "all" }`);
  console.log(`  App polls:    GET  /command  (every 15 min)`);
  console.log('═══════════════════════════════════════════════');
});
