package com.example.sample.controllers;

import com.example.sample.entities.Cat;
import com.example.sample.repositories.CatRepositoryStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class CatController {
    @Autowired
    private CatRepositoryStream catRepositoryStream;

    @GetMapping("/stream/cats")
    @Transactional(readOnly = true)
    public List<Cat> getStream(@RequestParam String name) {
        Stream<Cat> stream = catRepositoryStream.findAllByName(name);
        return stream
                .map(cat -> {
                    try {
                        System.out.println(cat.toString());
                        Thread.sleep(10000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (cat.getAge() == 2) stream.close();
                    return cat;
                })
                .onClose(() -> System.out.println("stream has closed!"))
                .collect(Collectors.toList());
    }
}
