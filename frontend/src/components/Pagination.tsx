type Props = {
  page: number; // bắt đầu từ 0
  size: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
};

export default function Pagination({ page, size, totalPages, totalElements, onChange }: Props) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);

  return (
    <div className="pagination">
      <span className="pagination-info">
        Hiển thị {from}–{to} / {totalElements} booking · Trang {page + 1}/{totalPages}
      </span>
      <div className="pagination-controls">
        <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Trước
        </button>
        <button type="button" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
          Sau
        </button>
      </div>
    </div>
  );
}