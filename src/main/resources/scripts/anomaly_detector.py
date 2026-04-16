#!/usr/bin/env python3
import json, sys, math
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import RobustScaler


def safe_float(v):
    try:
        if v is None or (isinstance(v, float) and math.isnan(v)):
            return None
        return float(v)
    except:
        return None


def build_df(history):
    rows = []
    for item in history:
        row = {"month": item["month"]}
        for k, v in item.get("kpis", {}).items():
            row[k] = safe_float(v)
        rows.append(row)
    df = pd.DataFrame(rows).sort_values("month").reset_index(drop=True)
    df["month_num"] = df["month"].str[5:7].astype(int)
    return df


def add_features(df):
    out = df.copy()

    out["cost_per_km"] = np.where(
        out.get("Distance_km", pd.Series([0]*len(out))).fillna(0) > 0,
        out.get("Total_Fleet_OPEX_EUR", pd.Series([np.nan]*len(out))) /
        out.get("Distance_km",          pd.Series([np.nan]*len(out))),
        np.nan
    )

    out["delivery_productivity"] = np.where(
        out.get("Total_delivery_hours", pd.Series([0]*len(out))).fillna(0) > 0,
        out.get("Total_volume_m3",      pd.Series([np.nan]*len(out))) /
        out.get("Total_delivery_hours", pd.Series([np.nan]*len(out))),
        np.nan
    )

    out["truck_productivity"] = np.where(
        out.get("Number_of_trucks_operating_during_the_month", pd.Series([0]*len(out))).fillna(0) > 0,
        out.get("Number_of_deliveries",                        pd.Series([np.nan]*len(out))) /
        out.get("Number_of_trucks_operating_during_the_month", pd.Series([np.nan]*len(out))),
        np.nan
    )

    out["driver_compliance_rate"] = np.where(
        out.get("Total Drivers", pd.Series([0]*len(out))).fillna(0) > 0,
        (out.get("Total Drivers",            pd.Series([np.nan]*len(out))) -
         out.get("Monthly Driver Violations", pd.Series([np.nan]*len(out))))
        / out.get("Total Drivers", pd.Series([np.nan]*len(out))),
        np.nan
    )

    return out


def fill_na(df):
    return df.apply(lambda col: col.fillna(col.median()), axis=0)


def run_ml(df):
    features = [
        "Number_of_deliveries", "Distance_km",
        "Total_Fleet_OPEX_EUR", "Unit_Cost_per_m3",
        "Total_volume_m3", "cost_per_km",
        "delivery_productivity", "truck_productivity"
    ]
    cols     = [c for c in features if c in df.columns]
    X        = fill_na(df[cols])
    scaler   = RobustScaler()
    X_scaled = scaler.fit_transform(X)
    model    = IsolationForest(
        n_estimators=300,
        contamination=min(0.15, max(0.08, 2/len(df))),
        random_state=42
    )
    model.fit(X_scaled)
    return model.decision_function(X_scaled), model.predict(X_scaled)


# -- KPIs dont la BAISSE est une bonne nouvelle ------------------
COST_KPIS     = {
    "Total_Fleet_OPEX_EUR", "Unit_Cost_per_m3", "cost_per_km",
    "Monthly Driver Violations",
}
# -- KPIs dont la HAUSSE est une bonne nouvelle ------------------
POSITIVE_KPIS = {
    "Number_of_deliveries", "Total_volume_m3",
    "driver_compliance_rate", "Driver_compliance_rate",
    "delivery_productivity", "truck_productivity",
    "Fleet_utilization_rate", "On_time_delivery_rate",
}


def _iqr_safe(series: pd.Series):
    """Retourne (mediane, iqr) avec protection contre IQR=0."""
    med = series.median()
    iqr = series.quantile(0.75) - series.quantile(0.25)
    if iqr < 1e-3:
        iqr = max(abs(med) * 0.1, 1.0)
    return med, iqr


def seasonal_anomaly(df, idx):
    row       = df.iloc[idx]
    month_num = row["month_num"]

    # compare uniquement vs meme mois annees precedentes
    hist = df[(df["month_num"] == month_num) & (df.index < idx)]
    if len(hist) < 2:
        return False, []

    alerts = []
    for k in ["Number_of_deliveries", "Total_volume_m3",
              "Distance_km", "Total_Fleet_OPEX_EUR"]:
        if k not in df.columns:
            continue
        v = safe_float(row.get(k))
        if v is None:
            continue

        med, iqr = _iqr_safe(hist[k].dropna())
        dev      = abs(v - med) / iqr

        if dev > 3:
            direction = "hausse" if v > med else "baisse"
            if k in COST_KPIS     and direction == "baisse":
                continue
            if k in POSITIVE_KPIS and direction == "hausse":
                continue
            alerts.append({
                "kpi":       k,
                "message":   f"{k} anormal vs meme mois",
                "deviation": round(dev, 2),
                "direction": direction
            })

    return len(alerts) > 0, alerts


def business_rules(row):
    alerts = []

    if safe_float(row.get("Delivery Truck Accident")):
        alerts.append({"severity": "critique",  "message": "Accident"})
    if safe_float(row.get("Spill/Cross-Fuel Incident")):
        alerts.append({"severity": "critique",  "message": "Incident carburant"})

    cost = safe_float(row.get("Unit_Cost_per_m3"))
    plan = safe_float(row.get("Plan_Unit_Cost_per_m3_EUR"))
    if cost and plan and cost > plan:
        alerts.append({"severity": "attention", "message": "Cout actuel > cout planifie"})

    fur = safe_float(row.get("Fleet_utilization_rate"))
    if fur:
        if fur < 0.85:
            alerts.append({"severity": "critique",  "message": "Faible utilisation de la flotte"})
        elif fur < 0.95:
            alerts.append({"severity": "attention", "message": "Moyenne utilisation de la flotte"})

    otd = safe_float(row.get("On_time_delivery_rate"))
    if otd:
        if otd < 0.90:
            alerts.append({"severity": "critique",  "message": "OTD faible"})
        elif otd < 0.95:
            alerts.append({"severity": "attention", "message": "OTD moyen"})

    comp = safe_float(row.get("driver_compliance_rate"))
    if comp:
        if comp < 0.75:
            alerts.append({"severity": "critique",  "message": "Faible taux de conformite des conducteurs"})
        elif comp < 0.85:
            alerts.append({"severity": "attention", "message": "Taux de conformite des conducteurs moyen"})

    return alerts


def top_contributors(df, idx):
    """
    Compare vs meme mois annees precedentes (coherent avec seasonal_anomaly).
    Fallback : tous mois precedents si historique < 2 points.
    Ajoute pct_vs_normal pour l explication manager.
    """
    current   = df.iloc[idx]
    month_num = current["month_num"]

    hist_same = df[(df["month_num"] == month_num) & (df.index < idx)]
    hist      = hist_same if len(hist_same) >= 2 else (df.iloc[:idx] if idx > 0 else df)

    contribs = []
    for col in df.columns:
        if col in ["month", "month_num"]:
            continue
        col_hist = hist[col].dropna()
        if len(col_hist) < 1 or col_hist.std() < 1e-3:
            continue

        v = safe_float(current.get(col))
        if v is None:
            continue

        med, iqr  = _iqr_safe(col_hist)
        dev       = min(abs(v - med) / iqr, 10.0)
        direction = "hausse" if v > med else "baisse"

        is_positive_change = (
                (col in COST_KPIS     and direction == "baisse") or
                (col in POSITIVE_KPIS and direction == "hausse")
        )

        pct = round((v - med) / med * 100, 1) if med != 0 else 0.0

        contribs.append({
            "feature":            col,
            "deviation":          round(dev, 2),
            "value":              round(v,   2),
            "median":             round(med, 2),
            "direction":          direction,
            "is_positive_change": is_positive_change,
            "pct_vs_normal":      pct
        })

    seen   = set()
    unique = []
    for c in sorted(contribs, key=lambda x: x["deviation"], reverse=True):
        key = c["feature"].lower().replace("_", "").replace(" ", "")
        if key not in seen:
            seen.add(key)
            unique.append(c)
        if len(unique) == 5:
            break
    return unique


# ================================================================
# EXPLICATION LISIBLE POUR LE MANAGER - generee en Python
# ================================================================
LABELS = {
    "Distance_km":               "la distance parcourue",
    "Total_Fleet_OPEX_EUR":      "le cout operationnel de la flotte",
    "Unit_Cost_per_m3":          "le cout unitaire",
    "cost_per_km":               "le cout par kilometre",
    "Number_of_deliveries":      "le nombre de livraisons",
    "Total_volume_m3":           "le volume transporté",
    "delivery_productivity":     "la productivité de livraison",
    "truck_productivity":        "la productivité des camions",
    "driver_compliance_rate":    "la conformite des conducteurs",
    "Driver_compliance_rate":    "la conformite des conducteurs",
    "Monthly Driver Violations": "les violations conducteurs",
    "Total Drivers":             "le nombre de conducteurs",
    "Planned_orders":            "les commandes planifiees",
    "Fleet_utilization_rate":    "le taux d'utilisation de la flotte",
    "On_time_delivery_rate":     "le taux de livraison a l'heure",
    "Number_of_loadings":        "le nombre de chargements",
    "Total_delivery_hours":      "les heures de livraison",
    "Number_of_trucks_operating_during_the_month": "le nombre de camions en service",
    "Delivery Truck Accident":   "les accidents de camion",
    "Spill/Cross-Fuel Incident": "les incidents carburant",
    "Plan_Unit_Cost_per_m3_EUR": "le cout planifie",
    "Total_hours_worked":        "les heures travaillées",
    "Total_loading_hours":       "les heures de chargement",
}

BUSINESS_ALERT_MESSAGES = {
    "Accident":
        "Un accident de camion a ete enregistre ce mois.",
    "Incident carburant":
        "Un incident de carburant / cross-fuel a ete signalé.",
    "Cout actuel > cout planifie":
        "Le cout unitaire reel depasse le budget planifie.",
    "Faible utilisation de la flotte":
        "Le taux d'utilisation de la flotte est inferieur à 85% - des camions sont immobilisés.",
    "Moyenne utilisation de la flotte":
        "Le taux d'utilisation de la flotte est entre 85% et 95%.",
    "OTD faible":
        "Le taux de livraison a l'heure est inferieur à 90% - des retards significatifs sont constates.",
    "OTD moyen":
        "Le taux de livraison a l'heure est entre 90% et 95%.",
    "Faible taux de conformite des conducteurs":
        "Le taux de conformite des conducteurs est critique (< 75%) - action RH requise.",
    "Taux de conformite des conducteurs moyen":
        "Le taux de conformite des conducteurs est moyen (75 - 85%).",
}


def _lbl(feat):
    return LABELS.get(feat, feat.replace("_", " ").lower())


def _fmt(feat, val):
    f = feat.lower()
    if "rate" in f or "compliance" in f:
        return f"{val*100:.1f}%"
    if "eur" in f or "opex" in f:
        return f"{val:,.0f} EUR"
    if "km" in f and "cost" not in f:
        return f"{val:,.0f} km"
    if "m3" in f:
        return f"{val:,.0f} m³"
    if val == int(val):
        return f"{int(val):,}"
    return f"{val:.2f}"


def generate_explanation(result: dict) -> str:
    month    = result["month"]
    level    = result["final_level"]
    b_alerts = result["business_alerts"]
    s_alerts = result["seasonal_alerts"]
    contribs = result["top_contributors"]

    lines = []

    # -- Intro --------------------------------------------------------
    if level == "critique":
        lines.append(
            f"[CRITIQUE] Mois {month} - Situation critique necessitant une action immediate.")
    elif level == "attention":
        lines.append(
            f"[ATTENTION] Mois {month} - Des anomalies ont ete detectees, une vigilance est recommandee.")
    else:
        lines.append(f"[NORMAL] Mois {month} - Situation globalement normale.")

    # -- Problèmes métier --------------------------------------------
    if b_alerts:
        lines.append("\nPROBLEMES DETECTES :")
        for a in b_alerts:
            icon = "[!]" if a["severity"] == "critique" else "[~]"
            msg  = BUSINESS_ALERT_MESSAGES.get(a["message"], a["message"])
            lines.append(f"  {icon} {msg}")

    # -- Anomalies saisonnières ---------------------------------------
    if s_alerts:
        lines.append("\nCOMPARAISON SAISONNIERE (vs meme mois annees precedentes) :")
        for sa in s_alerts:
            dtxt = "en hausse" if sa.get("direction") == "hausse" else "en baisse"
            lines.append(
                f"  [!] {_lbl(sa['kpi']).capitalize()} est {dtxt} de facon inhabituelle "
                f"(ecart : {sa['deviation']}x la variabilite normale du meme mois)."
            )

    # -- Facteurs preoccupants ----------------------------------------
    problems  = [c for c in contribs if not c["is_positive_change"] and c["deviation"] >= 0.8]
    positives = [c for c in contribs if     c["is_positive_change"] and c["deviation"] >= 0.8]

    if problems:
        lines.append("\nFACTEURS PREOCCUPANTS :")
        for c in problems:
            pct  = c["pct_vs_normal"]
            sign = "+" if pct > 0 else ""
            lines.append(
                f"  - {_lbl(c['feature']).capitalize()} : {_fmt(c['feature'], c['value'])} "
                f"({sign}{pct:.1f}% par rapport a la normale du meme mois, "
                f"attendu {_fmt(c['feature'], c['median'])})"
            )

    # -- Points positifs ----------------------------------------------
    if positives:
        lines.append("\nPOINTS POSITIFS CE MOIS :")
        for c in positives:
            pct  = abs(c["pct_vs_normal"])
            dtxt = "en hausse" if c["direction"] == "hausse" else "en baisse"
            lines.append(
                f"  - {_lbl(c['feature']).capitalize()} {dtxt} de {pct:.1f}% "
                f"vs la normale du meme mois amelioration."
            )

    # -- Recommandation -----------------------------------------------
    lines.append("\nRECOMMANDATION :")
    if level == "critique":
        recs = []
        if any("Accident" in a["message"] for a in b_alerts):
            recs.append("Investiguer immediatement les causes des accidents et renforcer la formation securite.")
        if any("Incident carburant" in a["message"] for a in b_alerts):
            recs.append("Verifier les procedures de ravitaillement et auditer les transporteurs.")
        if any("flotte" in a["message"] for a in b_alerts):
            recs.append("Verifier la disponibilite des camions et planifier la maintenance preventive.")
        if any("conducteurs" in a["message"] for a in b_alerts):
            recs.append("Organiser une reunion RH pour traiter les violations et renforcer la formation.")
        if any("OTD" in a["message"] for a in b_alerts):
            recs.append("Analyser les causes des retards de livraison et ajuster la planification.")
        if not recs:
            recs.append("Analyser les causes des derives et mettre en place un plan correctif immediat.")
        for r in recs:
            lines.append(f"  -> {r}")
    elif level == "attention":
        if problems:
            main = _lbl(problems[0]["feature"])
            lines.append(
                f"  -> Surveiller l'evolution de {main} et identifier les causes de la derive "
                f"avant qu'elle ne devienne critique."
            )
        else:
            lines.append("  -> Maintenir la vigilance et suivre les indicateurs la semaine prochaine.")
    else:
        lines.append("  -> Maintenir les bonnes pratiques en place.")

    return "\n".join(lines)


def final_decision(score, alerts, seasonal_flag):
    if any(a["severity"] == "critique" for a in alerts):
        return "critique"
    if score < -0.08 and seasonal_flag:
        return "attention"
    if alerts or score < 0 or seasonal_flag:
        return "attention"
    return "normal"


def run(data):
    df = build_df(data["history"])
    df = add_features(df)

    scores, preds = run_ml(df)
    results = []

    for i in range(len(df)):
        row = df.iloc[i]

        alerts                       = business_rules(row)
        seasonal_flag, seasonal_alts = seasonal_anomaly(df, i)
        top                          = top_contributors(df, i)

        result = {
            "month":            row["month"],
            "ml_score":         round(float(scores[i]), 4),
            "ml_anomaly":       int(preds[i] == -1),
            "business_alerts":  alerts,
            "seasonal_anomaly": seasonal_flag,
            "seasonal_alerts":  seasonal_alts,
            "top_contributors": top,
            "final_level":      final_decision(scores[i], alerts, seasonal_flag),
        }
        result["explanation"] = generate_explanation(result)
        results.append(result)

    return {
        "results": results,
        "model":   "Hybrid AI (ML + Rules + Seasonality)"
    }


if __name__ == "__main__":
    # Fix encodage Windows (cp1252 ne supporte pas les emojis)
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.stdin  = io.TextIOWrapper(sys.stdin.buffer,  encoding="utf-8")
    data = json.loads(sys.stdin.read())
    print(json.dumps(run(data), ensure_ascii=False))