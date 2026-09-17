// 프로필 이미지가 없을 때 쓰는 글자 아바타 색상.
// 가입 시점에 무작위로 배정한 것과 화면상 동일하게 보이도록, userId를 팔레트에 나눠서
// 항상 같은 색이 나오게 한다(따로 저장할 값이 없어도 됨).
// 원색은 너무 쨍해 보여서, 다른 배지들처럼 연한 배경 + 진한 글자색 조합을 쓴다.
const AVATAR_PALETTE = ['--primary', '--teal', '--blue', '--green', '--yellow', '--red']

export interface AvatarColors {
  bg: string
  fg: string
}

export function avatarColors(userId: number): AvatarColors {
  const name = AVATAR_PALETTE[Math.abs(userId) % AVATAR_PALETTE.length]
  return { bg: `var(${name}-l)`, fg: `var(${name})` }
}
