const Net = require('net');
const PORT = 8080;
const server = new Net.Server();
server.listen(PORT, () => console.log("server listening"));

server.on('connection',  (socket) => {
  let count = 0
  setInterval(() => {
    count++;
    socket.write(count.toString());
  }, 2000)
  socket.on('data', (chunk) => {
    console.log(`data: ${chunk}`)
  });
  socket.on('end', () => console.log('end'));
  socket.on('error', (err) => console.error(`error: ${err}`));
});

