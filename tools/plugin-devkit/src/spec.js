export const PLUGIN_API_VERSION = 1;
export const HOST_API_VERSION = 1;

export const CAPABILITIES = new Set([
  'searchSongs',
  'getLyrics',
  'searchCovers'
]);

export const CONFIG_FIELD_TYPES = new Set([
  'text',
  'password',
  'number',
  'switch',
  'dropdown',
  'textarea',
  'markdown'
]);

export const STANDARD_FIELD_KEYS = new Set([
  'title',
  'artist',
  'album',
  'album_artist',
  'genre',
  'date',
  'track_number',
  'disc_number',
  'composer',
  'lyricist',
  'comment',
  'lyrics',
  'cover_url',
  'language',
  'copyright',
  'rating',
  'replaygain_track_gain',
  'replaygain_track_peak',
  'replaygain_album_gain',
  'replaygain_album_peak',
  'replaygain_reference_loudness'
]);

export const HOST_APIS = new Set([
  'app.info',
  'app.userAgent',
  'runtime.info',
  'crypto.md5',
  'crypto.aesEcbPkcs5EncryptBase64',
  'crypto.aesEcbPkcs5EncryptHex',
  'crypto.aesEcbPkcs5DecryptBase64ToText',
  'base64.encodeText',
  'base64.decodeText',
  'base64.dropBytes',
  'base64.decodeBytes',
  'base64.encodeBytes',
  'bytes.xor',
  'bytes.xorBase64',
  'compression.inflateBytesToText',
  'compression.inflateBase64ToText',
  'http.getText',
  'http.postText',
  'http.postBytes',
  'http.get',
  'http.post',
  'http.getBytes',
  'http.postBytesResponse',
  'log.debug',
  'log.warn',
  'log.error'
]);

export const LIMITS = {
  manifestBytes: 128 * 1024,
  entryBytes: 1024 * 1024,
  singlePluginBytes: 5 * 1024 * 1024,
  zipEntries: 1000,
  zipDepth: 16
};
