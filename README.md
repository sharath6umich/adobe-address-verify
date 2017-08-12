# adobe-address-verify

Built at api to verify a bulk of addressesfrom a spreadsheet and get the longitute and lattitude of the address by leveraging Google's geolocation api

Simple and intuitive design, minimal http requests, optimal performance were my primary focus on designing the application 

Simple command line execution:
==============================

javac address-verify.java

java address-verify C:\Users<your name>\Downloads\test.csv



Notes on Google Sheets API:
===============================

•	Minimal calls to sheets api to get the address data from the spreadsheet

•	Using cell feed as only cell feed supports batch update.

•	Using batch update to improve performance. Using batching the number of http requests is reduced to just 1 http request to update all address entries in a spreadsheet

•	Basic checking of data integrity - Checking number of columns and column names


Google Geocoding API:
================================

•	Limit 1: 10 requests per second

•	Limit 2: 2500 requests per day

•	Designed Geocoding api calls to avoid >10 requests per second

•	Also added a simple caching mechanism so that if an address has already been looked up, we wouldn’t have to make a duplicate request to Geocoding api

•	Implemented simple retries in case of exceeding query limits and failing gracefully if exceeding 3 retries.

•	In case of multiple results, first result will be picked up as spreadsheet is 1:1 for each address.
