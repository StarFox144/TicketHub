package com.tickethub.venue.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "venues")
public class Venue extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String address;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<Section> sections = new ArrayList<>();

    protected Venue() {
    }

    public Venue(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }

    /** Додає секцію і генерує прямокутну сітку місць rowsCount × seatsPerRow. */
    public Section addSection(String sectionName, int rowsCount, int seatsPerRow) {
        boolean duplicate = sections.stream().anyMatch(s -> s.getName().equalsIgnoreCase(sectionName));
        if (duplicate) {
            throw new BusinessRuleException("Section '%s' already exists in venue".formatted(sectionName));
        }
        var section = new Section(this, sectionName, rowsCount, seatsPerRow);
        sections.add(section);
        return section;
    }

    public void update(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }

    public int capacity() {
        return sections.stream().mapToInt(Section::seatsCount).sum();
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
