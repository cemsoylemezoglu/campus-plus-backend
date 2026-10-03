package com.campusplus.backend.controller;


import com.campusplus.backend.entity.User;
import com.campusplus.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

private final UserService userService;

public UserController(UserService userService){
   this.userService = userService;

}


@PostMapping
public User createUser(@Valid @RequestBody User user){
return userService.saveUser(user);

}


@GetMapping
public List<User> getAllUsers(){

return userService.getAllUsers();

}

@GetMapping("/{id}")
public User getUserById(@PathVariable Long id){
return userService.getUserById(id);

}

    @PutMapping("/{id}")
    public User updateUser(
            @PathVariable Long id,
            @Valid @RequestBody User updatedUser) {

        return userService.updateUser(id, updatedUser);
    }


    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }



}
