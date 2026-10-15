package ru.main.spbplus;

public class Lesson {
    public final String number;
    public final String subject;
    public final String time;
    public final String room;
    public final String homework;
    public final String gradeBadge; // Оценка на бейдже в углу карточки (например "5" или "4.8")
    public final String boxTitle;   // Заголовок серого блока ("ДОМАШНЕЕ ЗАДАНИЕ:" или "ОЦЕНКИ:")

    public Lesson(String number, String subject, String time, String room, String homework, String gradeBadge, String boxTitle) {
        this.number = number != null ? number : "";
        this.subject = subject != null ? subject : "";
        this.time = time != null ? time : "";
        this.room = room != null ? room : "";
        this.homework = homework != null ? homework : "";
        this.gradeBadge = gradeBadge != null ? gradeBadge : "";
        this.boxTitle = boxTitle != null ? boxTitle : "ДОМАШНЕЕ ЗАДАНИЕ:";
    }
}