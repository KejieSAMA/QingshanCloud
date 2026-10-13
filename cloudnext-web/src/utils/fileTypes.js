export const FILE_VISUAL_KINDS = [
  'folder',
  'word',
  'excel',
  'ppt',
  'pdf',
  'image',
  'video',
  'audio',
  'archive',
  'code',
  'text',
  'app',
  'design',
  'cad',
  'ebook',
  'font',
  'certificate',
  'file'
];

const EXACT_NAME_KIND_MAP = {
  dockerfile: 'code',
  makefile: 'code',
  rakefile: 'code',
  gemfile: 'code',
  gradlew: 'code',
  '.env': 'text',
  '.gitignore': 'text',
  '.gitattributes': 'text',
  '.editorconfig': 'text'
};

export const FILE_EXTENSION_KIND_MAP = {
  doc: 'word',
  docx: 'word',
  dot: 'word',
  dotx: 'word',
  rtf: 'word',
  odt: 'word',
  wps: 'word',
  xls: 'excel',
  xlsx: 'excel',
  csv: 'excel',
  tsv: 'excel',
  ods: 'excel',
  et: 'excel',
  numbers: 'excel',
  ppt: 'ppt',
  pptx: 'ppt',
  odp: 'ppt',
  dps: 'ppt',
  pdf: 'pdf',
  jpg: 'image',
  jpeg: 'image',
  png: 'image',
  gif: 'image',
  webp: 'image',
  bmp: 'image',
  svg: 'image',
  ico: 'image',
  tif: 'image',
  tiff: 'image',
  heic: 'image',
  heif: 'image',
  avif: 'image',
  mp4: 'video',
  mov: 'video',
  avi: 'video',
  mkv: 'video',
  webm: 'video',
  flv: 'video',
  wmv: 'video',
  m4v: 'video',
  mpg: 'video',
  mpeg: 'video',
  '3gp': 'video',
  mp3: 'audio',
  wav: 'audio',
  flac: 'audio',
  aac: 'audio',
  ogg: 'audio',
  m4a: 'audio',
  wma: 'audio',
  mid: 'audio',
  midi: 'audio',
  zip: 'archive',
  rar: 'archive',
  '7z': 'archive',
  tar: 'archive',
  gz: 'archive',
  tgz: 'archive',
  bz2: 'archive',
  xz: 'archive',
  iso: 'archive',
  jar: 'archive',
  war: 'archive',
  java: 'code',
  kt: 'code',
  kts: 'code',
  js: 'code',
  ts: 'code',
  vue: 'code',
  py: 'code',
  go: 'code',
  rs: 'code',
  c: 'code',
  cpp: 'code',
  cc: 'code',
  h: 'code',
  hpp: 'code',
  cs: 'code',
  swift: 'code',
  php: 'code',
  rb: 'code',
  scala: 'code',
  groovy: 'code',
  html: 'code',
  htm: 'code',
  css: 'code',
  scss: 'code',
  less: 'code',
  json: 'code',
  xml: 'code',
  yml: 'code',
  yaml: 'code',
  sh: 'code',
  bash: 'code',
  zsh: 'code',
  ps1: 'code',
  bat: 'code',
  cmd: 'code',
  sql: 'code',
  gradle: 'code',
  txt: 'text',
  md: 'text',
  markdown: 'text',
  log: 'text',
  toml: 'text',
  ini: 'text',
  conf: 'text',
  properties: 'text',
  env: 'text',
  gitignore: 'text',
  gitattributes: 'text',
  editorconfig: 'text',
  apk: 'app',
  aab: 'app',
  ipa: 'app',
  exe: 'app',
  msi: 'app',
  dmg: 'app',
  pkg: 'app',
  deb: 'app',
  rpm: 'app',
  appimage: 'app',
  psd: 'design',
  ai: 'design',
  fig: 'design',
  sketch: 'design',
  xd: 'design',
  dwg: 'cad',
  dxf: 'cad',
  epub: 'ebook',
  mobi: 'ebook',
  azw3: 'ebook',
  ttf: 'font',
  otf: 'font',
  woff: 'font',
  woff2: 'font',
  pem: 'certificate',
  crt: 'certificate',
  cer: 'certificate',
  key: 'certificate',
  p12: 'certificate',
  pfx: 'certificate'
};

const FILE_TYPE_KIND_MAP = {
  folder: 'folder',
  word: 'word',
  doc: 'word',
  docx: 'word',
  excel: 'excel',
  xls: 'excel',
  xlsx: 'excel',
  ppt: 'ppt',
  pptx: 'ppt',
  pdf: 'pdf',
  image: 'image',
  video: 'video',
  audio: 'audio',
  archive: 'archive',
  code: 'code',
  text: 'text',
  app: 'app',
  apk: 'app',
  design: 'design',
  cad: 'cad',
  ebook: 'ebook',
  font: 'font',
  certificate: 'certificate',
  cert: 'certificate',
  file: 'file',
  other: 'file',
  unknown: 'file'
};

const normalize = (value) => String(value || '').trim().toLowerCase();

export const getFileExtension = (name) => {
  const normalized = normalize(name).split(/[?#]/)[0];
  if (!normalized) {
    return '';
  }
  if (EXACT_NAME_KIND_MAP[normalized]) {
    return normalized.startsWith('.') ? normalized.slice(1) : normalized;
  }
  const lastSegment = normalized.split(/[\\/]/).pop() || normalized;
  const dot = lastSegment.lastIndexOf('.');
  if (dot < 0 || dot === lastSegment.length - 1) {
    return '';
  }
  return lastSegment.slice(dot + 1);
};

const resolveKindFromName = (name) => {
  const normalized = normalize(name).split(/[?#]/)[0];
  if (EXACT_NAME_KIND_MAP[normalized]) {
    return EXACT_NAME_KIND_MAP[normalized];
  }
  const extension = getFileExtension(normalized);
  return FILE_EXTENSION_KIND_MAP[extension] || '';
};

const resolveKindFromContentType = (contentType) => {
  const type = normalize(contentType);
  if (!type) {
    return '';
  }
  if (type.startsWith('image/')) return 'image';
  if (type.startsWith('video/')) return 'video';
  if (type.startsWith('audio/')) return 'audio';
  if (type.startsWith('font/')) return 'font';
  if (type.startsWith('text/')) return 'text';
  if (type.includes('pdf')) return 'pdf';
  if (type.includes('msword') || type.includes('wordprocessingml')) return 'word';
  if (type.includes('spreadsheet') || type.includes('excel')) return 'excel';
  if (type.includes('presentation') || type.includes('powerpoint')) return 'ppt';
  if (type.includes('json') || type.includes('xml') || type.includes('yaml')) return 'code';
  if (type.includes('zip') || type.includes('rar') || type.includes('7z')
      || type.includes('tar') || type.includes('gzip') || type.includes('bzip2')
      || type.includes('xz') || type.includes('java-archive')) {
    return 'archive';
  }
  if (type.includes('android.package-archive') || type.includes('x-msdownload')
      || type.includes('x-msi') || type.includes('x-debian-package')
      || type.includes('x-rpm')) {
    return 'app';
  }
  if (type.includes('epub')) return 'ebook';
  if (type.includes('font') || type.includes('woff')) return 'font';
  if (type.includes('pkix') || type.includes('x-pem-file') || type.includes('x-x509')) {
    return 'certificate';
  }
  return '';
};

const resolveKindFromFileType = (fileType) => FILE_TYPE_KIND_MAP[normalize(fileType)] || '';

export const resolveFileVisualKind = ({ itemType, fileType, name, contentType } = {}) => {
  if (normalize(itemType) === 'folder' || normalize(fileType) === 'folder') {
    return 'folder';
  }
  const extension = getFileExtension(name);
  const type = normalize(contentType);
  if (extension === 'key' && (type.includes('keynote') || type.includes('presentation'))) {
    return 'ppt';
  }
  return resolveKindFromName(name)
    || resolveKindFromContentType(contentType)
    || resolveKindFromFileType(fileType)
    || 'file';
};
