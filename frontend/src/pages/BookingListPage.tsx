import { useEffect, useState } from 'react';
import Pagination from '../components/Pagination';
import { getLatestBookings } from "../services/bookingService";
import type { BookingListItem, BookingStatus } from '../types/booking';
import './BookingListPage.css';

type PageResponse<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

const STATUS_LABELS: Record<BookingStatus, string> = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DA_HUY: 'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG: 'Đã trả phòng',
}

const STATUS_CLASS_NAMES: Record<BookingStatus, string> = {
  CHO_XAC_NHAN: 'pending',
  DA_XAC_NHAN: 'confirmed',
  DA_HUY: 'cancelled',
  DA_NHAN_PHONG: 'checked-in',
  DA_TRA_PHONG: 'checked-out',
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}/${month}/${year}`
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)
}

export function BookingListPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<BookingListItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    // Nếu dịch vụ getLatestBookings hỗ trợ truyền số trang, bạn có thể truyền page vào đây. 
    // Nếu dùng fetch thuần, có thể thay thế bằng: fetch(`/api/bookings?page=${page}`).then(r => r.json())
    getLatestBookings(page)
      .then((res: any) => {
        if (!cancelled) {
          setData(res);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setError(
            reason instanceof Error
              ? reason.message
              : 'Không thể tải danh sách booking',
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [page]);

  const bookings = data?.content || [];

  return (
    <section className="panel booking-page">
      <div className="panel-heading">
        <div>
          <h2>Booking mới</h2>
          <p>Danh sách được sắp xếp theo thời điểm tạo mới nhất.</p>
        </div>
      </div>

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
                  <tr key={booking.bookingCode}>
                    <td>
                      <strong>{booking.bookingCode}</strong>
                    </td>
                    <td>{booking.guestName}</td>
                    <td>{booking.roomTypeName}</td>
                    <td>{formatDate(booking.checkInDate)}</td>
                    <td>{formatDate(booking.checkOutDate)}</td>
                    <td>{formatCurrency(booking.totalAmount)}</td>
                    <td>
                      <span
                        className={`booking-status ${
                          STATUS_CLASS_NAMES[booking.status]
                        }`}
                      >
                        {STATUS_LABELS[booking.status]}
                      </span>
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
              onChange={setPage}
            />
          )}
        </>
      )}
    </section>
  )
}