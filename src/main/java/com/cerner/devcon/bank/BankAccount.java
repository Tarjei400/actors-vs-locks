package com.cerner.devcon.bank;

public class BankAccount {

    int accountNumber; 
	
    double accountBalance;


    // to withdraw funds from the account
    public boolean transfer (double amount)  
    {
        double newAccountBalance;

        if( amount > accountBalance)
	{
             //there are not enough funds in the account
             return false; 
	}
		
	else
	{
            newAccountBalance = accountBalance - amount;
            Thread.yield();
            accountBalance = newAccountBalance;
            return true;
        }

    }

    public boolean deposit(double amount) 
    {
	double newAccountBalance;

	if( amount < 0.0)
	{
          return false; // can not deposit a negative amount
	}
		
	else
	{
          newAccountBalance = accountBalance + amount;
          Thread.yield();
          accountBalance = newAccountBalance;
	  return true;
         }

     }
}