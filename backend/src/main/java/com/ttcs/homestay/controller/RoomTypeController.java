package com.ttcs.homestay.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.service.RoomTypesService;

@Controller
public class RoomTypeController {

    @Autowired
    private RoomTypesService roomTypesService;

    @GetMapping("/room-types")
    public String listRoomTypes(Model model) {
        model.addAttribute("roomTypes", roomTypesService.getAllRoomTypes());
        model.addAttribute("roomType", new RoomType());
        return "room-types/list"; 
    }

    @GetMapping("/room-types/add")
    public String showAddForm(Model model) {
        model.addAttribute("roomType", new RoomType()); 
        return "room-types/add"; 
    }

    @PostMapping("/room-types/add")
    public String createRoomType(@ModelAttribute("roomType") RoomType roomType, Model model) {
        try {
            roomTypesService.createRoomType(roomType);
            return "redirect:/room-types?success";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("roomTypes", roomTypesService.getAllRoomTypes());
            return "room-types/list"; 
        }
    }

    // 1. Xử lý xóa loại phòng
    @GetMapping("/room-types/delete/{id}")
    public String deleteRoomType(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            roomTypesService.deleteRoomType(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa loại phòng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/room-types";
    }

    // 2. Xử lý chuyển đổi trạng thái (Ngừng bán / Đang bán)
    @GetMapping("/room-types/toggle-status/{id}")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            roomTypesService.toggleStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/room-types";
    }
}