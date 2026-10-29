package com.personal_finance.backend.controller;

import com.personal_finance.backend.model.Category;
import com.personal_finance.backend.model.Transaction;
import com.personal_finance.backend.service.CategorisationService;
import com.personal_finance.backend.service.CategorisationService.CategorisationSummary;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CategoryController {

    private final CategorisationService categorisation;

    public CategoryController(CategorisationService categorisation) {
        this.categorisation = categorisation;
    }

    public record CategoryOption(String name, String label) {
    }

    public record CategoryChange(Category category) {
    }

    // the categories to choose from, e.g. {"name": "EATING_OUT", "label": "Eating out"}
    @GetMapping("/categories")
    public List<CategoryOption> getCategories() {
        return Arrays.stream(Category.values())
                .map(category -> new CategoryOption(category.name(), category.getLabel()))
                .toList();
    }

    // set a transaction's category yourself; its merchant's other transactions follow
    @PutMapping("/transactions/{id}/category")
    public Transaction setCategory(@PathVariable long id, @RequestBody CategoryChange change) {
        if (change.category() == null) {
            throw new IllegalArgumentException("category is required");
        }
        return categorisation.setByUser(id, change.category());
    }

    // re-run merchant matching and rules over everything you haven't categorised yourself
    @PostMapping("/transactions/categorise")
    public CategorisationSummary recategoriseAll() {
        return categorisation.recategoriseAll();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
