package ru.main.spbplus;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LessonAdapter extends BaseAdapter {
    private final Context context;
    private final List<Lesson> items = new ArrayList<>();
    private boolean darkMode = false;

    public LessonAdapter(Context context) {
        this.context = context;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        notifyDataSetChanged();
    }

    public void setItems(List<Lesson> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int pos) { return items.get(pos); }

    @Override
    public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_lesson, parent, false);
            holder = new ViewHolder();
            holder.cardRoot = convertView.findViewById(R.id.cardRoot);
            holder.tvNumber = convertView.findViewById(R.id.tvLessonNumber);
            holder.tvSubject = convertView.findViewById(R.id.tvSubject);
            holder.tvTime = convertView.findViewById(R.id.tvLessonTime);
            holder.tvRoom = convertView.findViewById(R.id.tvRoom);
            holder.tvGradeBadge = convertView.findViewById(R.id.tvGradeBadge);
            holder.tvHomeworkTitle = convertView.findViewById(R.id.tvHomeworkTitle);
            holder.tvHomework = convertView.findViewById(R.id.tvHomework);
            holder.layoutHomework = convertView.findViewById(R.id.layoutHomework);

            holder.tvHomework.setMovementMethod(LinkMovementMethod.getInstance());
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Lesson l = items.get(pos);
        float density = context.getResources().getDisplayMetrics().density;

        // Цветовая схема карточки (Светлая / Тёмная тема)
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(12 * density);
        if (darkMode) {
            cardBg.setColor(Color.parseColor("#1E202B"));
            cardBg.setStroke((int) (1 * density), Color.parseColor("#2E3244"));
            holder.tvSubject.setTextColor(Color.parseColor("#ECEFF4"));
            holder.tvTime.setTextColor(Color.parseColor("#9AA5B1"));
            holder.tvRoom.setTextColor(Color.parseColor("#9AA5B1"));
            holder.tvHomeworkTitle.setTextColor(Color.parseColor("#9AA5B1"));
            holder.tvHomework.setTextColor(Color.parseColor("#D8DEE9"));
            holder.tvHomework.setLinkTextColor(Color.parseColor("#82B1FF"));
        } else {
            cardBg.setColor(Color.parseColor("#FFFFFF"));
            cardBg.setStroke((int) (1 * density), Color.parseColor("#E0E0E0"));
            holder.tvSubject.setTextColor(Color.parseColor("#212121"));
            holder.tvTime.setTextColor(Color.parseColor("#757575"));
            holder.tvRoom.setTextColor(Color.parseColor("#757575"));
            holder.tvHomeworkTitle.setTextColor(Color.parseColor("#616161"));
            holder.tvHomework.setTextColor(Color.parseColor("#333333"));
            holder.tvHomework.setLinkTextColor(Color.parseColor("#1976D2"));
        }
        holder.cardRoot.setBackground(cardBg);

        // Фон блока номера урока
        GradientDrawable numBg = new GradientDrawable();
        numBg.setCornerRadius(6 * density);
        if (darkMode) {
            numBg.setColor(Color.parseColor("#31274B"));
            holder.tvNumber.setTextColor(Color.parseColor("#B388FF"));
        } else {
            numBg.setColor(Color.parseColor("#EDE7F6"));
            holder.tvNumber.setTextColor(Color.parseColor("#5E35B1"));
        }
        holder.tvNumber.setBackground(numBg);

        // Фон вложенного блока ДЗ / Оценок
        GradientDrawable hwBg = new GradientDrawable();
        hwBg.setCornerRadius(8 * density);
        hwBg.setColor(darkMode ? Color.parseColor("#262938") : Color.parseColor("#F7F7F9"));
        holder.layoutHomework.setBackground(hwBg);

        // Заполнение данных
        if (l.number.isEmpty()) {
            holder.tvNumber.setVisibility(View.GONE);
        } else {
            holder.tvNumber.setVisibility(View.VISIBLE);
            holder.tvNumber.setText(l.number);
        }

        holder.tvSubject.setText(l.subject);
        holder.tvTime.setText(l.time.isEmpty() ? "Время не указано" : l.time);

        if (l.room.isEmpty()) {
            holder.tvRoom.setVisibility(View.GONE);
        } else {
            holder.tvRoom.setVisibility(View.VISIBLE);
            holder.tvRoom.setText("Каб. " + l.room);
        }

        // Бейдж оценки (зелёный для 5, сине-бирюзовый для 4, оранжевый для 3, красный для 2)
        if (l.gradeBadge.isEmpty()) {
            holder.tvGradeBadge.setVisibility(View.GONE);
        } else {
            holder.tvGradeBadge.setVisibility(View.VISIBLE);
            holder.tvGradeBadge.setText(l.gradeBadge);

            GradientDrawable gradeBg = new GradientDrawable();
            gradeBg.setCornerRadius(8 * density);
            gradeBg.setColor(getGradeColor(l.gradeBadge));
            holder.tvGradeBadge.setBackground(gradeBg);
        }

        holder.tvHomeworkTitle.setText(l.boxTitle);

        if (l.homework.trim().isEmpty()) {
            holder.layoutHomework.setVisibility(View.GONE);
        } else {
            holder.layoutHomework.setVisibility(View.VISIBLE);
            holder.tvHomework.setText(Html.fromHtml(l.homework.trim()));
        }

        return convertView;
    }

    private int getGradeColor(String grade) {
        String g = grade.replace("★", "").trim();
        try {
            double val = Double.parseDouble(g.split(",")[0].trim());
            if (val >= 4.5) return Color.parseColor("#2E7D32"); // Отлично (Зелёный)
            if (val >= 3.5) return Color.parseColor("#00897B"); // Хорошо (Бирюзовый)
            if (val >= 2.5) return Color.parseColor("#EF6C00"); // Удовл. (Оранжевый)
            return Color.parseColor("#C62828");                 // Неуд. (Красный)
        } catch (Exception ignored) {
            return Color.parseColor("#5E35B1");
        }
    }

    private static class ViewHolder {
        View cardRoot, layoutHomework;
        TextView tvNumber, tvSubject, tvTime, tvRoom, tvGradeBadge, tvHomeworkTitle, tvHomework;
    }
}