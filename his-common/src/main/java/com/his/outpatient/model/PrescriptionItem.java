package com.his.outpatient.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PrescriptionItem {
    private int id;
    private int prescriptionId;
    private int drugId;
    private String drugName;
    private String specification;
    private String dosage;
    private String frequency;
    private int days;
    private int quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String remark;
    private LocalDateTime createdAt;

    public PrescriptionItem() {
    }

    public PrescriptionItem(int id, int prescriptionId, int drugId, String drugName,
                           String specification, String dosage, String frequency,
                           int days, int quantity, String unit, BigDecimal unitPrice,
                           BigDecimal subtotal, String remark, LocalDateTime createdAt) {
        this.id = id;
        this.prescriptionId = prescriptionId;
        this.drugId = drugId;
        this.drugName = drugName;
        this.specification = specification;
        this.dosage = dosage;
        this.frequency = frequency;
        this.days = days;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPrescriptionId() {
        return prescriptionId;
    }

    public void setPrescriptionId(int prescriptionId) {
        this.prescriptionId = prescriptionId;
    }

    public int getDrugId() {
        return drugId;
    }

    public void setDrugId(int drugId) {
        this.drugId = drugId;
    }

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
