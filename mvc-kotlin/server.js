const http = require('http');
const url = require('url');

const host = 'localhost';
const port = 8080;

const requestListener = (req, res) => {
  const query = url.parse(req.url, true).query;
  setTimeout(() => {
    res.writeHead(200);
    res.end(query.name || "default");
  }, 10000);
}

const server = http.createServer(requestListener);

server.listen(port, host, () => {
  console.log(`Server is running on ${port}`)
});