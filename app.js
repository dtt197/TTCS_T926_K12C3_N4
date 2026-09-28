// app.js
// Điểm khởi động module Tiện nghi & Tham số homestay (Thành viên số 8).
// Import router này vào app chính của team, hoặc chạy độc lập để test.

const express = require('express');
const { sequelize } = require('./models');

const amenityRoutes = require('./routes/amenityRoutes');
const roomTypeRoutes = require('./routes/roomTypeRoutes');
const historyRoutes = require('./routes/historyRoutes');

const app = express();
app.use(express.json());

// TODO: gắn middleware xác thực thật của module Người 1 (auth) trước các route dưới đây
// app.use(authMiddleware);

app.use('/api', amenityRoutes);
app.use('/api', roomTypeRoutes);
app.use('/api', historyRoutes);

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ message: 'Lỗi hệ thống', detail: err.message });
});

const PORT = process.env.PORT || 3008;

async function start() {
  await sequelize.authenticate();
  console.log('DB connected');
  // Dùng migration SQL trong /migrations thay vì sync() ở môi trường thật
  app.listen(PORT, () => console.log(`Amenities module running on port ${PORT}`));
}

if (require.main === module) {
  start().catch((err) => console.error('Startup failed:', err));
}

module.exports = app;
