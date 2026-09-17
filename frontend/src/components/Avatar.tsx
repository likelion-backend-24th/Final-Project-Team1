import type { CSSProperties } from 'react'
import { avatarColors } from '../lib/avatarColor'
import { cdnImage } from '../lib/cloudinary'

interface AvatarProps {
  userId: number
  name: string
  imageUrl?: string | null
  size?: number
}

// 프로필 이미지가 있으면 사진을, 없으면 유저마다 고정된 색상의 이니셜을 보여준다.
export default function Avatar({ userId, name, imageUrl, size = 34 }: AvatarProps) {
  const base: CSSProperties = {
    width: size,
    height: size,
    borderRadius: '50%',
    flexShrink: 0,
    overflow: 'hidden',
  }

  if (imageUrl) {
    return <img src={cdnImage(imageUrl, size * 2)} alt={name} style={{ ...base, objectFit: 'cover' }} />
  }

  const { bg, fg } = avatarColors(userId)

  return (
    <div
      style={{
        ...base,
        background: bg,
        color: fg,
        fontWeight: 700,
        fontSize: size * 0.38,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {name[0]}
    </div>
  )
}
