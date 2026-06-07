package com.his.inpatient.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InpatientRecord {
    private int id;
    private String admissionNo;
    private int patientId;
    private int bedId;
    private int departmentId;
    private Integer attendingDoctorId;
    private LocalDate admissionDate;
    private LocalDate dischargeDate;
    private String admissionDiagnosis;
    private String dischargeDiagnosis;
    private String status;
    private String admissionReason;
    private String dischargeSummary;
    private String admissionType;
    private BigDecimal totalCost;
    private BigDecimal depositAmount;
    private BigDecimal paidAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Transient fields for display
    private String patientName = "";
    private String bedNo = "";
    private String doctorName = "";
    private String departmentName = "";

    public InpatientRecord() {
    }

    public InpatientRecord(int id, String admissionNo, int patientId, int bedId, int departmentId,
                           Integer attendingDoctorId, LocalDate admissionDate, LocalDate dischargeDate,
                           String admissionDiagnosis, String dischargeDiagnosis, String status,
                           String admissionType, BigDecimal totalCost, BigDecimal depositAmount,
                           String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.admissionNo = admissionNo;
        this.patientId = patientId;
        this.bedId = bedId;
        this.departmentId = departmentId;
        this.attendingDoctorId = attendingDoctorId;
        this.admissionDate = admissionDate;
        this.dischargeDate = dischargeDate;
        this.admissionDiagnosis = admissionDiagnosis;
        this.dischargeDiagnosis = dischargeDiagnosis;
        this.status = status;
        this.admissionType = admissionType;
        this.totalCost = totalCost;
        this.depositAmount = depositAmount;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAdmissionNo() {
        return admissionNo;
    }

    public void setAdmissionNo(String admissionNo) {
        this.admissionNo = admissionNo;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public int getBedId() {
        return bedId;
    }

    public void setBedId(int bedId) {
        this.bedId = bedId;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(int departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getAttendingDoctorId() {
        return attendingDoctorId;
    }

    public void setAttendingDoctorId(Integer attendingDoctorId) {
        this.attendingDoctorId = attendingDoctorId;
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) {
        this.admissionDate = admissionDate;
    }

    public LocalDate getDischargeDate() {
        return dischargeDate;
    }

    public void setDischargeDate(LocalDate dischargeDate) {
        this.dischargeDate = dischargeDate;
    }

    public String getAdmissionDiagnosis() {
        return admissionDiagnosis;
    }

    public void setAdmissionDiagnosis(String admissionDiagnosis) {
        this.admissionDiagnosis = admissionDiagnosis;
    }

    public String getDischargeDiagnosis() {
        return dischargeDiagnosis;
    }

    public void setDischargeDiagnosis(String dischargeDiagnosis) {
        this.dischargeDiagnosis = dischargeDiagnosis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAdmissionReason() {
        return admissionReason;
    }

    public void setAdmissionReason(String admissionReason) {
        this.admissionReason = admissionReason;
    }

    public String getDischargeSummary() {
        return dischargeSummary;
    }

    public void setDischargeSummary(String dischargeSummary) {
        this.dischargeSummary = dischargeSummary;
    }

    public String getAdmissionType() {
        return admissionType;
    }

    public void setAdmissionType(String admissionType) {
        this.admissionType = admissionType;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getBedNo() {
        return bedNo;
    }

    public void setBedNo(String bedNo) {
        this.bedNo = bedNo;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
}
