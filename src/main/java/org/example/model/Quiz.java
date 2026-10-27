package org.example.model;

public class Quiz {
    private String quizId;
    private String userId;
    private String quizName;
    private String quizCategory;

    public Quiz(){
    }

    public String getQuizId(){
        return quizId;
    }

    public void setQuizId(String quizId){
        this.quizId = quizId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuizName(){
        return quizName;
    }

    public void setQuizName(String quizName){
        this.quizName = quizName;
    }
    public String getQuizCategory(){
        return quizCategory;
    }

    public void setQuizCategory(String quizCategory){
        this.quizCategory = quizCategory;
    }
}
