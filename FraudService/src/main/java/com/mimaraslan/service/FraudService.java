package com.mimaraslan.service;

import com.mimaraslan.dto.TransferRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class FraudService {

    public boolean checkFraud(TransferRequest transfer) {

        // Transfer miktarını ve gönderen bakiyesini al
        BigDecimal amount = transfer.getAmount();
        BigDecimal fromBalance = transfer.getFromBalance();
        System.out.println("fromBalance: " + fromBalance);

        // Null kontrolleri
        if (amount == null || fromBalance == null) {
            System.out.println("Fraud: true (Null değer bulundu)");
            return true;
        }

        // Transfer miktarının geçerli olup olmadığını kontrol et
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Fraud: true (Miktar sıfır veya negatif)");
            return true;
        }

        // Bakiyenin negatif olup olmadığını kontrol et
        if (fromBalance.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Fraud: true (Bakiye negatif)");
            return true;
        }

        // Transfer miktarı bakiyeden büyükse şüpheli
        if (amount.compareTo(fromBalance) > 0) {
            System.out.println("Fraud: true (Miktar bakiye üzerinde)");
            return true;
        }

        // Hiçbir şüpheli durum yoksa
        System.out.println("Fraud: false");
        return false;
    }



}
