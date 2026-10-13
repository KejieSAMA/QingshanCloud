/**
 * 工具函数模块 - 格式化与问候语
 * Requirements: 6.2, 6.3, 7.7
 */

/**
 * 根据小时数返回时段问候语，支持用户姓名拼接
 * @param {number} hour - 小时数 (0-23)
 * @param {string|null|undefined} userName - 用户姓名
 * @returns {string} 问候语
 */
export function getGreeting(hour, userName) {
  let period
  if (hour >= 6 && hour <= 11) {
    period = '上午好'
  } else if (hour >= 12 && hour <= 17) {
    period = '下午好'
  } else {
    period = '晚上好'
  }

  if (userName) {
    return `${period}，${userName}`
  }
  return period
}

/**
 * 将字节数转换为人类可读格式
 * @param {number} bytes - 字节数
 * @returns {string} 格式化后的文件大小
 */
export function formatFileSize(bytes) {
  if (typeof bytes !== 'number' || isNaN(bytes) || bytes < 0) {
    return '--'
  }

  if (bytes < 1024) {
    return `${bytes} B`
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }

  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

/**
 * 格式化时间戳为相对或绝对时间字符串
 * @param {Date|number|string} timestamp - 时间戳
 * @param {Date} [now] - 当前时间（可选，默认 new Date()）
 * @returns {string} 格式化后的时间字符串
 */
export function formatTime(timestamp, now) {
  const date = timestamp instanceof Date ? timestamp : new Date(timestamp)
  if (isNaN(date.getTime())) {
    return ''
  }

  const current = now instanceof Date ? now : new Date()
  if (isNaN(current.getTime())) {
    return ''
  }

  const pad = (n) => String(n).padStart(2, '0')
  const hours = pad(date.getHours())
  const minutes = pad(date.getMinutes())

  // 判断是否同一天
  const isSameDay = (d1, d2) =>
    d1.getFullYear() === d2.getFullYear() &&
    d1.getMonth() === d2.getMonth() &&
    d1.getDate() === d2.getDate()

  // 判断是否是前一天
  const yesterday = new Date(current)
  yesterday.setDate(yesterday.getDate() - 1)

  if (isSameDay(date, current)) {
    return `今天 ${hours}:${minutes}`
  }

  if (isSameDay(date, yesterday)) {
    return `昨天 ${hours}:${minutes}`
  }

  // 更早的日期使用绝对格式
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

/**
 * 返回欢迎日期字符串
 * @returns {string} "欢迎回来！今天是 YYYY年M月D日 星期X"
 */
export function getDateString() {
  const now = new Date()
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const day = now.getDate()
  const weekday = weekdays[now.getDay()]
  return `欢迎回来！今天是 ${year}年${month}月${day}日 星期${weekday}`
}
