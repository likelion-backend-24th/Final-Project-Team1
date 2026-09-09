package com.team1.payment;

public record PaymentApprovalResult(
        PaymentApprovalOutcome outcome,
        Integer amount,
        String failureReason
) {
    public static PaymentApprovalResult success(Integer amount){
        return new PaymentApprovalResult(PaymentApprovalOutcome.SUCCESS,amount,null);
    }
    public static PaymentApprovalResult failedConfirmed(String failureReason){
        return new PaymentApprovalResult(PaymentApprovalOutcome.FAILED_CONFIRMED,null,failureReason);
    }
    public static PaymentApprovalResult unknown(String reason){
        return new PaymentApprovalResult(PaymentApprovalOutcome.UNKNOWN,null,reason);

    }
    public static PaymentApprovalResult amountMismatch(){
        return new PaymentApprovalResult(PaymentApprovalOutcome.AMOUNT_MISMATCH,null,null);
    }
}
