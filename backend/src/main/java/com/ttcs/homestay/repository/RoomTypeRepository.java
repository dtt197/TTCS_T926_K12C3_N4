package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.RoomType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

   

    /** S1-06 AC3: mã loại phòng duy nhất, không phân biệt hoa thường. */
    boolean existsByCodeIgnoreCase(String code);

    /** Dùng khi sửa: có loại phòng KHÁC đang dùng mã này không. */
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    /** Phòng gắn với loại phòng qua tên, nên tên cũng không được trùng. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
        /** S1-08 AC3: đếm số loại phòng đang gắn một tiện nghi (để chặn xoá). */
    long countByAmenitiesId(Long amenityId);

    List<RoomType> findAllByOrderByCodeAsc();
}