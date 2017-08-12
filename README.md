# adobe-address-verify

Built at api to verify a bulk of addressesfrom a spreadsheet and get the longitute and lattitude of the address by leveraging Google's geolocation api


Simple command line execution:

javac address-verify.java

java address-verify C:\Users<your name>\Downloads\test.csv

Notes on Google Sheets API:

•	Minimal calls to sheets api to get the address data from the spreadsheet
•	Using cell feed as only cell feed supports batch update.
•	Using batch update to improve performance. Using batching the number of http requests is reduced to just 1 http request to update all address entries in a spreadsheet
•	Basic checking of data integrity - Checking number of columns and column names

