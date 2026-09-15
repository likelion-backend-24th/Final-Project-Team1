import { useEffect } from 'react'

const SITE_NAME = 'ExpoHub'
const DEFAULT_TITLE = `${SITE_NAME} · 박람회 플랫폼`

/**
 * 브라우저 탭 제목을 "페이지 - ExpoHub" 로 맞춘다.
 * title 이 아직 없으면(상세 데이터 로딩 중 등) 기본 제목을 보여준다.
 */
export function usePageTitle(title?: string | null) {
  useEffect(() => {
    document.title = title ? `${title} - ${SITE_NAME}` : DEFAULT_TITLE
  }, [title])
}
