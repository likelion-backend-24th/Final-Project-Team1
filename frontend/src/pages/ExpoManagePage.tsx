import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import DetailImagesField from '../components/DetailImagesField'
import ImageUploadField from '../components/ImageUploadField'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'

const CATEGORIES = ['IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']

export default function ExpoManagePage() {
  usePageTitle('박람회 등록')
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { expoId } = useParams<{ expoId: string }>()
  const toast = useToast()

  // /host/expos/:expoId/edit 로 들어오면 수정, /host/expos/new?channelId= 면 등록.
  const editingId = expoId ? Number(expoId) : null
  const isEdit = editingId !== null
  const [channelId, setChannelId] = useState(Number(params.get('channelId')) || 0)

  const [form, setForm] = useState({
    title: '',
    description: '',
    category: CATEGORIES[0],
    region: '',
    venue: '',
    thumbnailUrl: '',
    detailImageUrls: [] as string[],
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [loadingExpo, setLoadingExpo] = useState(isEdit)

  // 소개글 초안. 키워드 입력과 생성 중 표시.
  const [keywords, setKeywords] = useState('')
  const [drafting, setDrafting] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    expoApi.getMyExpoById(editingId)
      .then(res => {
        const e = res.data
        setChannelId(e.channelId)
        setForm({
          title: e.title ?? '',
          description: e.description ?? '',
          category: e.category ?? CATEGORIES[0],
          region: e.region ?? '',
          venue: e.venue ?? '',
          thumbnailUrl: e.thumbnailUrl ?? '',
          detailImageUrls: e.detailImageUrls ?? [],
        })
      })
      .catch(() => setError('박람회 정보를 불러오지 못했습니다.'))
      .finally(() => setLoadingExpo(false))
  }, [editingId])

  // 키워드로 소개글 초안을 받아 입력창을 채운다. 저장은 하지 않는다 - 주최자가 읽고 고친 뒤
  // 저장 버튼을 눌러야 들어간다. 이미 쓴 소개문이 있으면 덮어쓰기 전에 확인을 받는다.
  async function handleDraft() {
    const list = keywords.split(',').map(k => k.trim()).filter(Boolean)
    if (list.length === 0) { toast('키워드를 먼저 입력해주세요.'); return }
    if (!channelId) { setError('채널을 먼저 생성해주세요.'); return }
    if (form.description.trim() && !confirm('이미 작성한 소개글이 있습니다. 초안으로 덮어쓸까요?')) {
      return
    }

    setDrafting(true)
    try {
      const res = await expoApi.draftDescription(channelId, {
        keywords: list,
        title: form.title || undefined,
        category: form.category,
        venue: form.venue || undefined,
        region: form.region || undefined,
      })
      if (res.data.applied && res.data.description) {
        setForm(p => ({ ...p, description: res.data.description as string }))
        toast('초안을 채웠습니다. 읽어보고 고쳐서 저장하세요.')
      } else {
        toast('초안을 만들지 못했습니다. 직접 작성해 주세요.')
      }
    } catch {
      toast('초안을 만들지 못했습니다. 직접 작성해 주세요.')
    } finally {
      setDrafting(false)
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!channelId) { setError('채널을 먼저 생성해주세요.'); return }
    setLoading(true)
    try {
      if (isEdit) {
        await expoApi.updateExpo(channelId, editingId, form)
        toast('박람회 정보가 수정되었습니다', 'success')
        navigate(`/host/expos/${editingId}/rounds`)
        return
      }
      const res = await expoApi.createExpo(channelId, form)
      toast('박람회가 등록되었습니다 (HIDDEN)', 'success')
      navigate(`/host/expos/${res.data.id}/rounds`, { state: { expo: res.data } })
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('종료된 박람회는 수정할 수 없습니다.')
      else if (e.status === 404) setError('해당 채널의 박람회를 찾을 수 없습니다.')
      else if (e.status === 403) setError('해당 채널의 소유자만 등록할 수 있습니다.')
      else setError(isEdit ? '박람회 수정에 실패했습니다.' : '박람회 등록에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 28 }}>
          ← 뒤로
        </button>

        <div style={{ maxWidth: 660 }}>
          <div className="page-header">
            <h1 className="page-title">{isEdit ? '박람회 수정' : '박람회 등록'}</h1>
            <p className="page-sub">
              {isEdit
                ? '공개된 박람회를 수정하면 방문자 화면에 즉시 반영됩니다.'
                : '등록 후 회차를 추가하면 공개 버튼이 활성화됩니다.'}
            </p>
          </div>

          {!channelId && !isEdit && (
            <div className="alert alert-warning" style={{ marginBottom: 20 }}>
              ⚠ 채널을 먼저 생성해야 박람회를 등록할 수 있습니다.{' '}
              <span
                style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }}
                onClick={() => navigate('/host/channel/new')}
              >
                채널 생성하기
              </span>
            </div>
          )}

          <div className="card" style={{ padding: 32 }}>
            {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">박람회 제목 <span className="req">*</span></label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="예: 2026 테크 잡페어"
                  value={form.title}
                  onChange={e => setForm(p => ({ ...p, title: e.target.value }))}
                  maxLength={200}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">카테고리 <span className="req">*</span></label>
                <select
                  className="form-input"
                  value={form.category}
                  onChange={e => setForm(p => ({ ...p, category: e.target.value }))}
                >
                  {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">지역</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="서울"
                    value={form.region}
                    onChange={e => setForm(p => ({ ...p, region: e.target.value }))}
                    maxLength={50}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">장소(venue)</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="코엑스"
                    value={form.venue}
                    onChange={e => setForm(p => ({ ...p, venue: e.target.value }))}
                    maxLength={200}
                  />
                </div>
              </div>

              <ImageUploadField
                value={form.thumbnailUrl}
                onChange={url => setForm(p => ({ ...p, thumbnailUrl: url }))}
              />

              <DetailImagesField
                value={form.detailImageUrls}
                onChange={urls => setForm(p => ({ ...p, detailImageUrls: urls }))}
              />

              <div className="form-group" style={{ marginBottom: 28 }}>
                <label className="form-label">박람회 소개</label>

                {/* 키워드로 초안 생성. AI 가 대신 쓰는 게 아니라 빈 입력창을 채워주는 도구다 -
                    그래서 배지를 붙이지 않고, 채운 뒤엔 주최자가 고쳐서 자기 글로 만든다. */}
                <div className="draft-box">
                  <input
                    className="form-input"
                    type="text"
                    placeholder="키워드를 쉼표로 구분해 입력 (예: AI, 스타트업, 네트워킹)"
                    value={keywords}
                    onChange={e => setKeywords(e.target.value)}
                    disabled={drafting}
                    onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleDraft() } }}
                  />
                  <button
                    type="button"
                    className="btn btn-outline"
                    onClick={handleDraft}
                    disabled={drafting || !channelId}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    {drafting ? '만드는 중...' : '✨ 초안 만들기'}
                  </button>
                </div>

                <textarea
                  className="form-input"
                  placeholder={'방문자에게 보여질 박람회 소개를 입력하세요.\n\n줄을 바꾸면 화면에도 그대로 나옵니다.'}
                  value={form.description}
                  onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
                  rows={10}
                  disabled={drafting}
                  style={{ minHeight: 220, resize: 'vertical', lineHeight: 1.7 }}
                />
                <p style={{ fontSize: 12, color: 'var(--sub)' }}>
                  줄바꿈과 문단이 상세 페이지에 그대로 유지됩니다. 길이 제한은 없습니다
                  {form.description.length > 0 && ` (현재 ${form.description.length.toLocaleString()}자)`}.
                  {form.description.length > 300 && ' 300자를 넘으면 상세 페이지에서 접히고 “더보기”가 붙습니다.'}
                </p>
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block btn-lg"
                disabled={loading || loadingExpo || !channelId}
              >
                {loading ? '저장 중...' : isEdit ? '수정 저장' : '박람회 등록'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}
