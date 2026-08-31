import { useMemo } from 'react';

type Star = {
  top: string;
  left: string;
  size: number;
  delay: string;
  duration: string;
};

export default function StarField() {
  const stars = useMemo<Star[]>(() => {
    const arr: Star[] = [];
    for (let i = 0; i < 60; i++) {
      arr.push({
        top: `${Math.random() * 100}%`,
        left: `${Math.random() * 100}%`,
        size: Math.random() * 2 + 1,
        delay: `${Math.random() * 3}s`,
        duration: `${2 + Math.random() * 3}s`,
      });
    }
    return arr;
  }, []);

  return (
    <div className="pointer-events-none fixed inset-0 overflow-hidden">
      {/* deep gradient */}
      <div className="absolute inset-0 bg-gradient-to-b from-night-950 via-night-900 to-night-950" />
      {/* nebula glow */}
      <div className="absolute -top-1/4 right-1/4 h-[600px] w-[600px] rounded-full bg-moon-800/20 blur-[120px]" />
      <div className="absolute bottom-0 -left-1/4 h-[500px] w-[500px] rounded-full bg-accent-600/10 blur-[120px]" />
      {/* stars */}
      {stars.map((s, i) => (
        <span
          key={i}
          className="absolute rounded-full bg-moon-100 animate-twinkle"
          style={{
            top: s.top,
            left: s.left,
            width: `${s.size}px`,
            height: `${s.size}px`,
            animationDelay: s.delay,
            animationDuration: s.duration,
          }}
        />
      ))}
    </div>
  );
}
