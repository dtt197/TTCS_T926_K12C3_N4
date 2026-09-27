package com.ttcs.homestay.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.service.RoomTypesService;

@Controller
@RequestMapping("/room-types")
public class RoomTypeController {

    @Autowired
    private RoomTypesService roomTypesService;

    // Hiển thị danh sách và form thêm mới
    @GetMapping
    public String listRoomTypes(Model model) {
        model.addAttribute("roomTypes", roomTypesService.getAllRoomTypes());
        model.addAttribute("roomType", new RoomType());
        return "room-types"; // Tên file HTML giao diện (tùy thuộc vào project của nhóm)
    }

    // Xử lý thêm mới loại phòng có chặn trùng mã
    @PostMapping("/add")
    public String createRoomType(@ModelAttribute("roomType") RoomType roomType, Model model) {
        try {
            roomTypesService.createRoomType(roomType);
            return "redirect:/room-types?success";
        } catch (RuntimeException e) {
            // Giữ lại dữ liệu người dùng đã nhập và đẩy thông báo lỗi ra giao diện
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("roomTypes", roomTypesService.getAllRoomTypes());
            return "room-types"; // Trả về lại trang nhập liệu mà không bị mất dữ liệu
        }
    }
}