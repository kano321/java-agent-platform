package com.example;

import java.util.List;

public class OrderService {

    private final List<String> orders;

    public OrderService(List<String> orders) {
        this.orders = orders;
    }

    // TODO: extract this method into a pricing strategy
    public double calculateTotal(List<Double> prices) {
        double total = 0;
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i) > 100) {
                total += prices.get(i) * 0.9;
            } else {
                total += prices.get(i);
            }
        }
        System.out.println("Total is " + total);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return total;
    }

    public void save() {
        try {
            orders.add("new order");
        } catch (Exception e) {
        }
    }
}
