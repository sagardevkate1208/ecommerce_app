package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AddressRequest;
import com.ecommerce.auth.dto.AddressResponse;
import com.ecommerce.auth.dto.ProfileResponse;
import com.ecommerce.auth.dto.UpdateProfileRequest;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints act on the currently authenticated user (principal = user id from the JWT). */
@RestController
@RequestMapping("/api/users/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse me(@AuthenticationPrincipal String userId) {
        return profileService.getProfile(Long.valueOf(userId));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal String userId,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(Long.valueOf(userId), request);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal String userId) {
        return profileService.listAddresses(Long.valueOf(userId));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse addAddress(@AuthenticationPrincipal String userId,
                                      @Valid @RequestBody AddressRequest request) {
        return profileService.addAddress(Long.valueOf(userId), request);
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal String userId, @PathVariable Long id,
                                         @Valid @RequestBody AddressRequest request) {
        return profileService.updateAddress(Long.valueOf(userId), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal String userId, @PathVariable Long id) {
        profileService.deleteAddress(Long.valueOf(userId), id);
    }
}
