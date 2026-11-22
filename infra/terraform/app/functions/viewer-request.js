// 既定のビヘイビア（フロントの S3）の CloudFront Functions（docs/12「CloudFront」）。
// - SNS のクローラーからの /posts/{id} は、OGP の HTML を返す API（/api/ogp/posts/{id}）に回す（F-14）
// - 画面のパス（/home・/users/... など、拡張子のないパス）は /index.html にする（SPA）
import cf from 'cloudfront';

// リンクのプレビューを作るクローラー。JavaScript を実行しないので、画面からは投稿の内容を読めない。
// 人が使うアプリ内ブラウザ（LINE など）の User-Agent に含まれる語は入れない（OGP の HTML から画面へ戻ったとき、また回されてしまうため）
var CRAWLERS = /Twitterbot|facebookexternalhit|Slackbot|Discordbot|LinkedInBot|Hatena|TelegramBot|Applebot|redditbot|SkypeUriPreview|Pinterest/i;
var POST = /^\/posts\/([0-9a-fA-F-]{36})\/?$/;

function handler(event) {
  var request = event.request;
  var uri = request.uri;
  var ua = request.headers['user-agent'] ? request.headers['user-agent'].value : '';

  var post = uri.match(POST);
  if (post && CRAWLERS.test(ua)) {
    request.uri = '/api/ogp/posts/' + post[1];
    cf.selectRequestOriginById('api');
    return request;
  }

  var last = uri.substring(uri.lastIndexOf('/') + 1);
  if (last.indexOf('.') === -1) {
    request.uri = '/index.html';
  }
  return request;
}
