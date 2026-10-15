import { useEffect, useRef, useState } from 'react'
import { ApiRequestError } from '../services/apiClient'
import {
  completeRepair, getMaintenanceDetail, getMaintenanceRequests, updateMaintenance, verifyRepair,
  type MaintenanceDetail, type MaintenancePage as Page, type VendorInput,
} from '../services/maintenanceService'
import './MaintenancePage.css'

const labels: Record<string, string> = {
  CHO_TIEP_NHAN: 'Chờ tiếp nhận', DA_TIEP_NHAN: 'Đã tiếp nhận', DANG_XU_LY: 'Đang xử lý',
  CHO_XAC_NHAN: 'Chờ xác nhận sửa chữa', CHO_VE_SINH: 'Chờ vệ sinh', HOAN_TAT: 'Hoàn tất',
  ACCEPTED: 'Tiếp nhận', VENDOR_UPDATED: 'Lưu đơn vị sửa chữa', PROCESSING_STARTED: 'Bắt đầu xử lý',
  REPAIR_COMPLETED: 'Báo sửa chữa xong', VERIFIED: 'Xác nhận sửa chữa',
  REPAIR_REJECTED: 'Yêu cầu sửa lại', CLEANING_COMPLETED: 'Hoàn tất vệ sinh',
}
const time = (value: string) => new Date(value).toLocaleString('vi-VN')
const message = (error: unknown) => error instanceof Error ? error.message : 'Không thể tải dữ liệu. Vui lòng thử lại.'

export function MaintenancePage({ requestId, owner, onSelect, onClose, housekeeping = false }: {
  requestId: number | null; owner: boolean; housekeeping?: boolean; onSelect: (id: number | null) => void; onClose: () => void
}) {
  return <section className="maintenance-page" aria-label="Yêu cầu bảo trì">
    <header><h2>Yêu cầu bảo trì</h2><button onClick={onClose}>Đóng</button></header>
    {requestId === null
      ? <MaintenanceList onSelect={onSelect} />
      : <MaintenanceDetailView key={requestId} id={requestId} owner={owner} housekeeping={housekeeping} onBack={() => onSelect(null)} />}
  </section>
}

function MaintenanceList({ onSelect }: { onSelect: (id: number) => void }) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setData(null)
    setError('')
    getMaintenanceRequests(page, controller.signal).then(result => {
      if (!controller.signal.aborted) setData(result)
    }).catch(cause => { if (!controller.signal.aborted) setError(message(cause)) })
    return () => controller.abort()
  }, [page, reload])
  return <>
    {error && <p role="alert">{error} <button onClick={() => setReload(x => x + 1)}>Thử lại</button></p>}
    {!data && !error && <p role="status">Đang tải…</p>}
    {data && <>
      {data.content.length === 0 && <p>Chưa có yêu cầu bảo trì.</p>}
      <div className="maintenance-list">{data.content.map(item => <button key={item.id} onClick={() => onSelect(item.id)}>
        <strong>#{item.id} · Phòng {item.roomNumber} · {labels[item.status] ?? item.status}</strong>
        <span>{item.severity === 'NGHIEM_TRONG' ? 'Nghiêm trọng' : 'Nhẹ'} · {item.reportedByName} · {time(item.reportedAt)}</span>
        <span>{item.description}</span>
      </button>)}</div>
      <footer><button disabled={page === 0} onClick={() => setPage(x => x - 1)}>Trước</button>
        <span>Trang {data.totalPages ? page + 1 : 0}/{data.totalPages}</span>
        <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(x => x + 1)}>Tiếp</button></footer>
    </>}
  </>
}

function MaintenanceDetailView({ id, owner, onBack }: { id: number; owner: boolean; housekeeping: boolean; onBack: () => void }) {
  const [data, setData] = useState<MaintenanceDetail | null>(null)
  const [vendor, setVendor] = useState<VendorInput>({ name: '', phone: '', notes: '' })
  const [repairNotes, setRepairNotes] = useState('')
  const [verificationNotes, setVerificationNotes] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const busyRef = useRef(false)
  const lifecycle = useRef<AbortController | null>(null)
  function apply(result: MaintenanceDetail) {
    setData(result)
    setVendor({ name: result.request.vendorName ?? '', phone: result.request.vendorPhone ?? '', notes: result.request.vendorNotes ?? '' })
  }
  useEffect(() => {
    const controller = new AbortController()
    lifecycle.current = controller
    setData(null)
    setError('')
    getMaintenanceDetail(id, controller.signal).then(result => {
      if (!controller.signal.aborted) apply(result)
    }).catch(cause => { if (!controller.signal.aborted) setError(message(cause)) })
    return () => controller.abort()
  }, [id, reload])

  async function act(action: 'accept' | 'vendor' | 'start' | 'repair-completed' | 'verify-pass' | 'verify-fail') {
    const controller = lifecycle.current
    if (busyRef.current || !controller || controller.signal.aborted) return
    busyRef.current = true
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const updated = action === 'repair-completed'
        ? await completeRepair(id, repairNotes, controller.signal)
        : action === 'verify-pass' || action === 'verify-fail'
          ? await verifyRepair(id, action === 'verify-pass', verificationNotes, controller.signal)
          : await updateMaintenance(id, action, action === 'accept' ? undefined : vendor, controller.signal)
      if (controller.signal.aborted) return
      // Update controls immediately even if the following history refresh fails.
      setData(current => current ? { ...current, request: updated } : current)
      setNotice(action === 'accept' ? 'Đã tiếp nhận yêu cầu.' : action === 'start' ? 'Đã bắt đầu xử lý.' : action === 'vendor' ? 'Đã lưu đơn vị sửa chữa.' : action === 'repair-completed' ? 'Đã ghi nhận hoàn tất sửa chữa.' : action === 'verify-pass' ? 'Đã xác nhận sửa chữa đạt.' : action === 'verify-fail' ? 'Đã yêu cầu đơn vị sửa lại.' : '')
      const result = await getMaintenanceDetail(id, controller.signal)
      if (!controller.signal.aborted) apply(result)
    } catch (cause) {
      if (controller.signal.aborted) return
      setError(cause instanceof ApiRequestError && cause.status === 409
        ? 'Yêu cầu đã đổi trạng thái. Vui lòng tải lại trước khi thao tác tiếp.' : message(cause))
      // Disable stale actions after a conflict or an uncertain network outcome until refreshed.
      setData(null)
    } finally {
      busyRef.current = false
      if (!controller.signal.aborted) setBusy(false)
    }
  }
  const request = data?.request
  return <>
    <button disabled={busy} onClick={onBack}>← Danh sách</button>
    {error && <p role="alert">{error} <button disabled={busy} onClick={() => setReload(x => x + 1)}>Tải lại</button></p>}
    {notice && <p role="status">{notice}</p>}
    {!request && !error && <p role="status">Đang tải…</p>}
    {request && <>
      <h3>Yêu cầu #{request.id} · Phòng {request.roomNumber}</h3>
      <dl>
        <dt>Trạng thái</dt><dd>{labels[request.status] ?? request.status}</dd>
        <dt>Mức độ</dt><dd>{request.severity === 'NGHIEM_TRONG' ? 'Nghiêm trọng' : 'Nhẹ'}</dd>
        <dt>Mô tả</dt><dd>{request.description}</dd>
        <dt>Người báo</dt><dd>{request.reportedByName}</dd>
        <dt>Thời gian báo</dt><dd>{time(request.reportedAt)}</dd>
        {request.acceptedAt && <><dt>Tiếp nhận</dt><dd>{request.acceptedByName} · {time(request.acceptedAt)}</dd></>}
        {request.processingStartedAt && <><dt>Bắt đầu xử lý</dt><dd>{time(request.processingStartedAt)}</dd></>}
        {request.repairCompletedAt && <><dt>Sửa xong</dt><dd>{request.repairCompletedByName} · {time(request.repairCompletedAt)}</dd><dt>Kết quả</dt><dd>{request.repairNotes}</dd></>}
        {request.verifiedAt && <><dt>Kiểm tra</dt><dd>{request.verifiedByName} · {time(request.verifiedAt)}</dd><dt>Ghi chú kiểm tra</dt><dd>{request.verificationNotes}</dd></>}
        {request.cleanedAt && <><dt>Vệ sinh hoàn tất</dt><dd>{request.cleanedByName} · {time(request.cleanedAt)}</dd><dt>Ghi chú vệ sinh</dt><dd>{request.cleaningNotes}</dd></>}
      </dl>
      <p>Housekeeping hoàn tất vệ sinh sau sửa chữa từ danh sách phòng cần dọn bằng thao tác Đã dọn sạch.</p>
      {owner && request.status === 'CHO_TIEP_NHAN' && <button disabled={busy} onClick={() => void act('accept')}>Tiếp nhận</button>}
      {owner && request.status === 'DA_TIEP_NHAN' ? <form onSubmit={event => { event.preventDefault(); void act('start') }}>
        <fieldset disabled={busy}><legend>Đơn vị sửa chữa bên ngoài</legend>
          <p>Chủ homestay tự liên hệ đơn vị sửa chữa và lưu thông tin tại đây.</p>
          <label>Tên đơn vị<input required maxLength={200} value={vendor.name} onChange={e => setVendor({ ...vendor, name: e.target.value })} /></label>
          <label>Số điện thoại<input required type="tel" minLength={6} maxLength={30} value={vendor.phone} onChange={e => setVendor({ ...vendor, phone: e.target.value })} /></label>
          <label>Ghi chú<textarea maxLength={2000} value={vendor.notes} onChange={e => setVendor({ ...vendor, notes: e.target.value })} /></label>
          <button type="button" onClick={event => { if (event.currentTarget.form?.reportValidity()) void act('vendor') }}>Lưu thông tin</button>
          <button type="submit">Bắt đầu xử lý</button>
        </fieldset>
      </form> : request.vendorName && <dl>
        <dt>Đơn vị sửa chữa</dt><dd>{request.vendorName}</dd>
        <dt>Số điện thoại</dt><dd>{request.vendorPhone}</dd>
        <dt>Ghi chú</dt><dd>{request.vendorNotes || '—'}</dd>
      </dl>}
      {owner && request.status === 'DANG_XU_LY' && <form onSubmit={event => { event.preventDefault(); void act('repair-completed') }}>
        <fieldset disabled={busy}><legend>Ghi nhận sửa chữa hoàn tất</legend>
          <label>Kết quả sửa chữa<textarea required maxLength={2000} value={repairNotes} onChange={e => setRepairNotes(e.target.value)} /></label>
          <button type="submit">Đã sửa xong</button>
        </fieldset>
      </form>}
      {owner && request.status === 'CHO_XAC_NHAN' && <fieldset disabled={busy}>
        <legend>Kiểm tra sửa chữa</legend>
        <label>Ghi chú kiểm tra / lý do yêu cầu làm lại<textarea required maxLength={2000} value={verificationNotes} onChange={e => setVerificationNotes(e.target.value)} /></label>
        <button type="button" onClick={event => { if (event.currentTarget.closest('fieldset')?.querySelector('textarea')?.reportValidity()) void act('verify-pass') }}>Đạt</button>
        <button type="button" onClick={event => { if (event.currentTarget.closest('fieldset')?.querySelector('textarea')?.reportValidity()) void act('verify-fail') }}>Không đạt, yêu cầu làm lại</button>
      </fieldset>}
      {!owner && <p>Bạn có quyền theo dõi yêu cầu bảo trì.</p>}
      <h3>Lịch sử xử lý</h3>
      {data.history.length === 0 ? <p>Chưa có lịch sử xử lý.</p> : <ol>{data.history.map(entry => <li key={entry.id}>
        <strong>{labels[entry.action] ?? entry.action}</strong> · {entry.actorName} · {time(entry.occurredAt)}
        <p>{labels[entry.previousStatus]} → {labels[entry.newStatus]}</p>
        {entry.vendorName && <p>{entry.vendorName} · {entry.vendorPhone}</p>}
        {entry.notes && <p>{entry.notes}</p>}
      </li>)}</ol>}
    </>}
  </>

}
