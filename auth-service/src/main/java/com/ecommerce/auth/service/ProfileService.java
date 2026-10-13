package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AddressRequest;
import com.ecommerce.auth.dto.AddressResponse;
import com.ecommerce.auth.dto.ProfileResponse;
import com.ecommerce.auth.dto.UpdateProfileRequest;
import com.ecommerce.auth.entity.Address;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.exception.ApiException;
import com.ecommerce.auth.repository.AddressRepository;
import com.ecommerce.auth.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

    private final UserRepository users;
    private final AddressRepository addresses;

    public ProfileService(UserRepository users, AddressRepository addresses) {
        this.users = users;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return toProfile(load(userId));
    }

    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = load(userId);
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(req.phone());
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addresses.findByUserIdOrderByIdAsc(userId).stream().map(this::toAddress).toList();
    }

    public AddressResponse addAddress(Long userId, AddressRequest req) {
        User user = load(userId);
        Address address = new Address();
        address.setUser(user);
        apply(address, req);
        // The first address is always the default one.
        if (user.getAddresses().isEmpty()) {
            address.setDefaultAddress(true);
        }
        if (address.isDefaultAddress()) {
            clearDefault(user);
        }
        user.getAddresses().add(address);
        users.flush(); // assigns the generated id
        return toAddress(address);
    }

    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest req) {
        User user = load(userId);
        Address address = find(user, addressId);
        if (req.defaultAddress()) {
            clearDefault(user);
        }
        apply(address, req);
        return toAddress(address);
    }

    public void deleteAddress(Long userId, Long addressId) {
        User user = load(userId);
        Address address = find(user, addressId);
        boolean wasDefault = address.isDefaultAddress();
        user.getAddresses().remove(address);
        if (wasDefault && !user.getAddresses().isEmpty()) {
            user.getAddresses().get(0).setDefaultAddress(true);
        }
    }

    private User load(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Address find(User user, Long addressId) {
        return addresses.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void clearDefault(User user) {
        user.getAddresses().forEach(a -> a.setDefaultAddress(false));
    }

    private void apply(Address a, AddressRequest req) {
        a.setLine1(req.line1());
        a.setLine2(req.line2());
        a.setCity(req.city());
        a.setState(req.state());
        a.setPostalCode(req.postalCode());
        a.setCountry(req.country());
        a.setDefaultAddress(req.defaultAddress());
    }

    private ProfileResponse toProfile(User u) {
        return new ProfileResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getRole().name());
    }

    private AddressResponse toAddress(Address a) {
        return new AddressResponse(a.getId(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                a.getPostalCode(), a.getCountry(), a.isDefaultAddress());
    }
}
