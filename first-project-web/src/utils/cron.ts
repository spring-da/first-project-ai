export interface CronFieldResult {
  source: string
  values: number[]
  wildcard: boolean
}

export interface ParsedCron {
  expression: string
  fields: {
    second: CronFieldResult
    minute: CronFieldResult
    hour: CronFieldResult
    day: CronFieldResult
    month: CronFieldResult
    weekday: CronFieldResult
  }
  summary: string
}

const MONTHS: Record<string, number> = { JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6, JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12 }
const WEEKDAYS: Record<string, number> = { SUN: 1, MON: 2, TUE: 3, WED: 4, THU: 5, FRI: 6, SAT: 7 }
const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function readValue(source: string, aliases: Record<string, number>, fieldName: string) {
  const upper = source.toUpperCase()
  const alias = aliases[upper]
  const value = alias ?? Number(source)
  if (!Number.isInteger(value)) throw new Error(`${fieldName}中的“${source}”不是有效数字`)
  return value
}

function parseField(source: string, min: number, max: number, fieldName: string, aliases: Record<string, number> = {}) {
  const wildcard = source === '*' || source === '?'
  const values = new Set<number>()
  for (const segment of source.split(',')) {
    if (!segment) throw new Error(`${fieldName}包含空值`)
    const [base, stepSource, extra] = segment.split('/')
    if (extra !== undefined) throw new Error(`${fieldName}中的步长格式不正确`)
    const step = stepSource === undefined ? 1 : Number(stepSource)
    if (!Number.isInteger(step) || step <= 0) throw new Error(`${fieldName}的步长必须是正整数`)

    let start = min
    let end = max
    if (base !== '*' && base !== '?') {
      if (base.includes('-')) {
        const [startSource, endSource, rangeExtra] = base.split('-')
        if (!startSource || !endSource || rangeExtra !== undefined) throw new Error(`${fieldName}中的范围格式不正确`)
        start = readValue(startSource, aliases, fieldName)
        end = readValue(endSource, aliases, fieldName)
      } else {
        start = readValue(base, aliases, fieldName)
        end = stepSource === undefined ? start : max
      }
    }
    if (fieldName === '星期' && start === 0) start = 1
    if (fieldName === '星期' && end === 0) end = 1
    if (start < min || start > max || end < min || end > max || start > end) {
      throw new Error(`${fieldName}必须在 ${min}–${max} 范围内`)
    }
    for (let value = start; value <= end; value += step) values.add(value)
  }
  return { source, values: [...values].sort((a, b) => a - b), wildcard }
}

function compactValues(field: CronFieldResult, unit: string) {
  if (field.wildcard) return `每${unit}`
  if (field.values.length > 4) return `${field.values[0]}–${field.values.at(-1)}${unit}`
  return `${field.values.join('、')}${unit}`
}

function describeCron(parsed: Omit<ParsedCron, 'summary'>) {
  const { second, minute, hour, day, month, weekday } = parsed.fields
  const time = `${compactValues(hour, '时')} ${compactValues(minute, '分')} ${compactValues(second, '秒')}`
  if (!weekday.wildcard) return `${weekday.values.map((value) => WEEKDAY_LABELS[value - 1]).join('、')}，${time}触发`
  if (!day.wildcard) return `每月 ${day.values.join('、')} 日，${time}触发`
  if (!month.wildcard) return `每年 ${month.values.join('、')} 月，${time}触发`
  return `${time}触发`
}

export function parseCronExpression(source: string): ParsedCron {
  const parts = source.trim().replace(/\s+/g, ' ').split(' ')
  if (parts.length === 5) parts.unshift('0')
  if (parts.length !== 6) throw new Error('请输入 5 位或 6 位 CRON 表达式')
  const [secondSource, minuteSource, hourSource, daySource, monthSource, weekdaySource] = parts as [string, string, string, string, string, string]
  const parsed: Omit<ParsedCron, 'summary'> = {
    expression: parts.join(' '),
    fields: {
      second: parseField(secondSource, 0, 59, '秒'),
      minute: parseField(minuteSource, 0, 59, '分钟'),
      hour: parseField(hourSource, 0, 23, '小时'),
      day: parseField(daySource, 1, 31, '日期'),
      month: parseField(monthSource, 1, 12, '月份', MONTHS),
      weekday: parseField(weekdaySource, 1, 7, '星期', WEEKDAYS),
    },
  }
  return { ...parsed, summary: describeCron(parsed) }
}

function contains(field: CronFieldResult, value: number) {
  return field.values.includes(value)
}

export function nextCronRuns(source: string, after = new Date(), count = 8) {
  const parsed = parseCronExpression(source)
  const result: Date[] = []
  const startDay = new Date(after.getFullYear(), after.getMonth(), after.getDate())
  for (let offset = 0; offset < 366 * 5 && result.length < count; offset++) {
    const day = new Date(startDay)
    day.setDate(startDay.getDate() + offset)
    const daysInMonth = new Date(day.getFullYear(), day.getMonth() + 1, 0).getDate()
    const weekday = day.getDay() + 1
    if (day.getDate() > daysInMonth
      || !contains(parsed.fields.month, day.getMonth() + 1)
      || !contains(parsed.fields.day, day.getDate())
      || !contains(parsed.fields.weekday, weekday)) continue
    for (const hour of parsed.fields.hour.values) {
      for (const minute of parsed.fields.minute.values) {
        for (const second of parsed.fields.second.values) {
          const candidate = new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour, minute, second)
          if (candidate > after) result.push(candidate)
          if (result.length >= count) return result
        }
      }
    }
  }
  return result
}
