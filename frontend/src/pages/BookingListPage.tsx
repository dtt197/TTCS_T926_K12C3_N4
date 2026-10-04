import './BookingListPage.css'
import type { BookingStatus, BookingListItem } from '../types/booking'
import { useEffect, useState, type FormEvent } from 'react'
import { searchBookings } from '../services/bookingService'

const STATUS_LABELS: Record<string, string> = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DA_HUY: 'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG: 'Đã trả phòng',
  DA_HET_HAN: 'Đã hết hạn',
}

const STATUS_CLASS_NAMES: Record<string, string> = {
  CHO_XAC_NHAN: 'pending',
  DA_XAC_NHAN: 'confirmed',
  DA_HUY: 'cancelled',
  DA_NHAN_PHONG: 'checked-in',
  DA_TRA_PHONG: 'checked-out',
  DA_HET_HAN: 'cancelled',
}

const EMPTY_FILTERS = {
  status: '' as BookingStatus | '',
  checkInFrom: '',
  checkInTo: '',
  keyword: '',
}

function formatDate(value: string) {
  if (!value) return ''
  const parts = value.split('-')
  if (parts.length === 3) {
    const [year, month, day] = parts
    return `${day}/${month}/${year}`
  }
  return value
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)
}

export function BookingListPage() {
  const [bookings, setBookings] = useState<BookingListItem[]>([])
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<BookingStatus | ''>('')
  const [checkInFrom, setCheckInFrom] = useState('')
  const [checkInTo, setCheckInTo] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function loadBookings(nextPage = 0) {
    setLoading(true)
    setError('')

    try {
      const result = await searchBookings({
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        checkInFrom: checkInFrom || undefined,
        checkInTo: checkInTo || undefined,
        page: nextPage,
        size: 20,
      })

      setBookings(result.content || [])
      setPage(result.page ?? 0)
      setTotalPages(result.totalPages ?? 0)
      setTotalElements(result.totalElements ?? 0)
    } catch (err) {
      setBookings([])
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách booking.',
      )
    } finally {
      setLoading(false)
    }
  }

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS);

  useEffect(() => {
    void loadBookings(0)
  }, [])

    getLatestBookings(page, appliedFilters)
      .then((res) => {
        if (!cancelled) {
          setData(res);
        }
  function handleFilterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (checkInFrom && checkInTo && checkInTo < checkInFrom) {
      setError('Ngày nhận phòng đến phải sau hoặc bằng ngày nhận phòng từ')
      return
    }

    void loadBookings(0)
  }

  function clearFilters() {
    setKeyword('')
    setStatus('')
    setCheckInFrom('')
    setCheckInTo('')
    setError('')
    setLoading(true)
    void searchBookings({ page: 0, size: 20 })
      .then((result) => {
        setBookings(result.content || [])
        setPage(result.page ?? 0)
        setTotalPages(result.totalPages ?? 0)
        setTotalElements(result.totalElements ?? 0)
      })
      .catch((err) => {
        setBookings([])
        setError(
          err instanceof Error
            ? err.message
            : 'Không thể tải danh sách booking.',
        )
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [page, appliedFilters]);

  function handleFilterSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (
      filters.checkInFrom &&
      filters.checkInTo &&
      filters.checkInTo < filters.checkInFrom
    ) {
      setError('Ngày trả bộ lọc phải sau hoặc bằng ngày nhận')
      return
    }

    setError(null)
    setPage(0)
    setAppliedFilters({ ...filters })
  }

  function handleResetFilters() {
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setPage(0)
    setError(null)
  }

  const bookings = data?.content || [];

        setLoading(false)
      })
  }

  return (
    <section className="panel booking-page">
      <div className="panel-heading">
        <div>
          <h2>Booking mới</h2>
          <p>Danh sách được sắp xếp theo thời điểm tạo mới nhất.</p>
        </div>
      </div>

      <form
        className="booking-filters"
        onSubmit={handleFilterSubmit}
      >
        <label>
          Từ khóa
          <input
            type="search"
            value={filters.keyword}
            placeholder="Tên khách hoặc số điện thoại"
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                keyword: event.target.value,
              }))
            }
          />
        </label>

        <label>
          Trạng thái
          <select
            value={filters.status}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                status: event.target.value as BookingStatus | '',
              }))
            }
          >
            <option value="">Tất cả trạng thái</option>
            <option value="CHO_XAC_NHAN">Chờ xác nhận</option>
            <option value="DA_XAC_NHAN">Đã xác nhận</option>
            <option value="DA_HUY">Đã huỷ</option>
            <option value="DA_NHAN_PHONG">Đã nhận phòng</option>
            <option value="DA_TRA_PHONG">Đã trả phòng</option>
          </select>
        </label>

        <label>
          Nhận phòng từ
          <input
            type="date"
            value={filters.checkInFrom}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                checkInFrom: event.target.value,
              }))
            }
          />
        </label>

        <label>
          Nhận phòng đến
          <input
            type="date"
            value={filters.checkInTo}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                checkInTo: event.target.value,
              }))
            }
          />
        </label>

        <button type="submit">Lọc</button>

        <button
          type="button"
          onClick={handleResetFilters}
        <input
          type="search"
          value={keyword}
          placeholder="Tên khách hoặc số điện thoại"
          onChange={(event) => setKeyword(event.target.value)}
        />

        <select
          value={status}
          onChange={(event) =>
            setStatus(event.target.value as BookingStatus | '')
          }
        >
          <option value="">Tất cả trạng thái</option>
          <option value="CHO_XAC_NHAN">Chờ xác nhận</option>
          <option value="DA_XAC_NHAN">Đã xác nhận</option>
          <option value="DA_HUY">Đã huỷ</option>
          <option value="DA_NHAN_PHONG">Đã nhận phòng</option>
          <option value="DA_TRA_PHONG">Đã trả phòng</option>
          <option value="DA_HET_HAN">Đã hết hạn</option>
        </select>

        <input
          type="date"
          value={checkInFrom}
          onChange={(event) => setCheckInFrom(event.target.value)}
          title="Nhận phòng từ"
        />

        <input
          type="date"
          value={checkInTo}
          onChange={(event) => setCheckInTo(event.target.value)}
          title="Nhận phòng đến"
        />

        <button type="submit">
          Lọc
        </button>

        <button
          type="button"
          onClick={clearFilters}
        >
          Xoá lọc
        </button>
      </form>

      {error && <div className="alert">{error}</div>}

      {loading ? (
        <p className="empty-state">Đang tải danh sách booking...</p>
      ) : bookings.length === 0 ? (
        <p className="empty-state">Chưa có booking nào.</p>
      ) : (
        <>
          <div className="booking-table-wrapper">
            <table className="booking-table">
              <caption className="visually-hidden">
                Danh sách booking mới
              </caption>

              <thead>
                <tr>
                  <th>Mã booking</th>
                  <th>Tên khách</th>
                  <th>Loại phòng</th>
                  <th>Ngày nhận</th>
                  <th>Ngày trả</th>
                  <th>Tổng tiền</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>

              <tbody>
                {bookings.map((booking) => (
                  <tr 
                    key={booking.bookingCode} 
                    className={booking.holdExpired ? 'row-hold-expired' : ''}
                  >
                    <td>
                      <strong>{booking.bookingCode}</strong>
                    </td>
                    <td>
                      <div>{booking.guestName}</div>
                      {booking.guestPhone && (
                        <small>{booking.guestPhone}</small>
                      )}
                    </td>
                    <td>{booking.roomTypeNameSnapshot}</td>
                    <td>{formatDate(booking.checkInDate)}</td>
                    <td>{formatDate(booking.checkOutDate)}</td>
                    <td>{formatCurrency(booking.totalAmount)}</td>
                    <td>
                      <span
                        className={`booking-status ${
                          STATUS_CLASS_NAMES[booking.status] ?? 'pending'
                        }`}
                      >
                        {STATUS_LABELS[booking.status] ?? booking.status}
                      </span>
                      {booking.holdExpired && (
                        <span className="badge-expired">
                          Quá hạn giữ chỗ 24 giờ
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data && (
            <Pagination
              page={data.page}
              size={data.size}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onChange={(nextPage) => {
                setLoading(true)
                setPage(nextPage)
              }}
            />
          )}
          <div className="booking-pagination">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => void loadBookings(page - 1)}
            >
              Trang trước
            </button>

            <span>
              Trang {page + 1} / {Math.max(totalPages, 1)} ({totalElements} booking)
            </span>

            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => void loadBookings(page + 1)}
            >
              Trang sau
            </button>
          </div>
        </>
      )}
    </section>
  )
}