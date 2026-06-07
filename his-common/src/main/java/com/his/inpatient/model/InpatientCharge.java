package com.his.inpatient.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InpatientCharge {
    private int id;
    private int inpatientId;
    private String chargeType;
    private String chargeItem;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDate chargeDate;
    private Integer operatorId;
    private boolean isPaid;
    private String doctorName;
    private String remark;
    private LocalDateTime createdAt;

    public InpatientCharge() {
    }

    public InpatientCharge(int id, int inpatientId, String chargeType, String chargeItem,
                            int quantity, BigDecimal unitPrice, BigDecimal amount,
                            LocalDate chargeDate, Integer operatorId, boolean isPaid,
                            String remark, LocalDateTime createdAt) {
        this.id = id;
        this.inpatientId = inpatientId;
        this.chargeType = chargeType;
        this.chargeItem = chargeItem;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.chargeDate = chargeDate;
        this.operatorId = operatorId;
        this.isPaid = isPaid;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getInpatientId() {
        return inpatientId;
    }

    public void setInpatientId(int inpatientId) {
        this.inpatientId = inpatientId;
    }

    public String getChargeType() {
        return chargeType;
    }

    public void setChargeType(String chargeType) {
        this.chargeType = chargeType;
    }

    public String getChargeItem() {
        return chargeItem;
    }

    public void setChargeItem(String chargeItem) {
        this.chargeItem = chargeItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getChargeDate() {
        return chargeDate;
    }

    public void setChargeDate(LocalDate chargeDate) {
        this.chargeDate = chargeDate;
    }

    public Integer getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Integer operatorId) {
        this.operatorId = operatorId;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public void setPaid(boolean paid) {
        isPaid = paid;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
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
