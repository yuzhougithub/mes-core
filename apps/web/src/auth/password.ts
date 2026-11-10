/** Same rules the API enforces: length, 3 of 4 classes, and no username. */
export function passwordProblems(password: string, username: string): string[] {
  const problems: string[] = []
  if (Array.from(password).length < 8) problems.push('密码至少 8 位')
  if (new TextEncoder().encode(password).length > 72) problems.push('密码不能超过 72 字节')
  let classes = 0
  if (/[A-Z]/.test(password)) classes += 1
  if (/[a-z]/.test(password)) classes += 1
  if (/[0-9]/.test(password)) classes += 1
  if (/[^A-Za-z0-9\s]/.test(password)) classes += 1
  if (classes < 3) problems.push('密码须包含大小写字母、数字、符号中的至少 3 类')
  const name = username.trim().toLowerCase()
  if (name && password.toLowerCase().includes(name)) problems.push('密码不能包含用户名')
  return problems
}
