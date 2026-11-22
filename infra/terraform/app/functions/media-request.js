// /media/* のビヘイビアの CloudFront Functions。先頭の /media を外して、画像のバケットのキーにする
// （アプリは画像の URL を /media/<キー> で返す。STORAGE_PUBLIC_BASE_URL=/media）
function handler(event) {
  var request = event.request;
  request.uri = request.uri.replace(/^\/media/, '');
  return request;
}
